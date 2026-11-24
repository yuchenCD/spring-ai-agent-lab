package io.github.yuchencd.agentlab.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hand-rolled MCP stdio client: newline-delimited JSON-RPC 2.0 over a child
 * process' stdin/stdout, with request ids correlated through futures.
 *
 * <p>Deliberately no SDK: the protocol is small, and keeping the wire format
 * visible matches the project's approach to the LLM API (ADR-0001).</p>
 */
public class StdioMcpClient implements McpClient {

  private static final String PROTOCOL_VERSION = "2024-11-05";

  private final ObjectMapper objectMapper;
  private final Process process;
  private final BufferedWriter stdin;
  private final Duration timeout;
  private final AtomicLong requestIds = new AtomicLong();
  private volatile boolean closed;
  private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
  private final Thread reader;

  public StdioMcpClient(String command, List<String> args, Map<String, String> env, ObjectMapper objectMapper, Duration timeout)
      throws IOException {
    this.objectMapper = objectMapper;
    this.timeout = timeout;

    List<String> commandLine = new ArrayList<>();
    commandLine.add(command);
    commandLine.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(commandLine);
    if (env != null && !env.isEmpty()) {
      builder.environment().putAll(env);
    }
    builder.redirectError(ProcessBuilder.Redirect.INHERIT);
    this.process = builder.start();
    this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    this.reader = Thread.ofVirtual().name("mcp-reader-" + process.pid()).start(this::readLoop);
  }

  @Override
  public String initialize() {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("protocolVersion", PROTOCOL_VERSION);
    params.putObject("capabilities");
    ObjectNode clientInfo = params.putObject("clientInfo");
    clientInfo.put("name", "spring-ai-agent-lab");
    clientInfo.put("version", "0.4.0");

    JsonNode result = request("initialize", params);
    notify("notifications/initialized", objectMapper.createObjectNode());
    return result.path("protocolVersion").asText(PROTOCOL_VERSION);
  }

  @Override
  public List<McpToolSpec> listTools() {
    JsonNode result = request("tools/list", objectMapper.createObjectNode());
    List<McpToolSpec> tools = new ArrayList<>();
    for (JsonNode tool : result.path("tools")) {
      Map<String, Object> schema = objectMapper.convertValue(
          tool.path("inputSchema"),
          objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
      tools.add(new McpToolSpec(
          tool.path("name").asText(),
          tool.path("description").asText(""),
          schema));
    }
    return List.copyOf(tools);
  }

  @Override
  public String callTool(String name, String argumentsJson) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("name", name);
    try {
      params.set("arguments", argumentsJson == null || argumentsJson.isBlank()
          ? objectMapper.createObjectNode()
          : objectMapper.readTree(argumentsJson));
    } catch (IOException exception) {
      throw new McpException("工具参数不是合法 JSON：" + exception.getMessage(), exception);
    }
    JsonNode result = request("tools/call", params);
    StringBuilder text = new StringBuilder();
    for (JsonNode content : result.path("content")) {
      if ("text".equals(content.path("type").asText())) {
        if (!text.isEmpty()) {
          text.append('\n');
        }
        text.append(content.path("text").asText());
      }
    }
    String joined = text.toString();
    if (result.path("isError").asBoolean(false)) {
      return "ERROR: " + joined;
    }
    return joined.isEmpty() ? "(no text content)" : joined;
  }

  @Override
  public void close() {
    closed = true;
    try {
      stdin.close();
    } catch (IOException ignored) {
      // the process may already be gone
    }
    process.destroy();
    try {
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private JsonNode request(String method, ObjectNode params) {
    long id = requestIds.incrementAndGet();
    CompletableFuture<JsonNode> future = new CompletableFuture<>();
    pending.put(id, future);

    ObjectNode message = objectMapper.createObjectNode();
    message.put("jsonrpc", "2.0");
    message.put("id", id);
    message.put("method", method);
    message.set("params", params);
    send(message);

    try {
      JsonNode response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      pending.remove(id);
      if (response.has("error")) {
        throw new McpException("MCP 调用 " + method + " 失败：" + response.path("error").path("message").asText());
      }
      return response.path("result");
    } catch (TimeoutException exception) {
      pending.remove(id);
      throw new McpException("MCP 调用 " + method + " 超时（" + timeout.toMillis() + "ms）", exception);
    } catch (ExecutionException exception) {
      pending.remove(id);
      throw new McpException("MCP 调用 " + method + " 失败：" + exception.getCause(), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      pending.remove(id);
      throw new McpException("MCP 调用 " + method + " 被中断", exception);
    }
  }

  private void notify(String method, ObjectNode params) {
    ObjectNode message = objectMapper.createObjectNode();
    message.put("jsonrpc", "2.0");
    message.put("method", method);
    message.set("params", params);
    send(message);
  }

  private synchronized void send(ObjectNode message) {
    try {
      stdin.write(objectMapper.writeValueAsString(message));
      stdin.write('\n');
      stdin.flush();
    } catch (IOException exception) {
      throw new McpException("写入 MCP 进程失败：" + exception.getMessage(), exception);
    }
  }

  private void readLoop() {
    try (BufferedReader stdout = new BufferedReader(
        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = stdout.readLine()) != null) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
          continue;
        }
        JsonNode message;
        try {
          message = objectMapper.readTree(trimmed);
        } catch (IOException exception) {
          continue;
        }
        if (!message.has("id") || message.path("id").isNull()) {
          continue;
        }
        long id = message.path("id").asLong();
        CompletableFuture<JsonNode> future = pending.remove(id);
        if (future != null) {
          future.complete(message);
        }
      }
    } catch (IOException exception) {
      if (!closed) {
        throw new UncheckedIOException("读取 MCP 进程失败", exception);
      }
    } finally {
      if (!closed) {
        pending.values().forEach(future ->
            future.completeExceptionally(new McpException("MCP 进程已退出")));
        pending.clear();
      }
    }
  }
}
