package io.github.yuchencd.agentlab.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yuchencd.agentlab.config.AgentProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Calls any OpenAI-compatible {@code /chat/completions} endpoint (DeepSeek by default).
 *
 * <p>Uses the JDK {@link HttpClient} directly instead of an SDK so the wire format
 * is visible and testable — see {@code docs/adr/0001-raw-http-client-over-framework.md}.</p>
 *
 * <p>Streaming follows the OpenAI SSE dialect: {@code data: {...}} lines carrying
 * {@code choices[0].delta}, terminated by {@code data: [DONE]}. Text deltas are
 * forwarded immediately; tool-call deltas are assembled by {@code index} because
 * providers split function names and arguments across chunks.</p>
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

  private final AgentProperties.Llm config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final URI endpoint;

  public OpenAiCompatibleLlmClient(AgentProperties.Llm config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
    this.endpoint = URI.create(stripTrailingSlash(config.baseUrl()) + "/chat/completions");
  }

  @Override
  public String providerName() {
    return config.provider();
  }

  @Override
  public Llm.Completion complete(List<Llm.Message> messages, List<Llm.ToolSpec> tools) {
    HttpResponse<String> response = send(buildRequest(messages, tools, false), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw httpError(response.statusCode(), response.body());
    }
    return parse(response.body());
  }

  @Override
  public Llm.Completion stream(
      List<Llm.Message> messages,
      List<Llm.ToolSpec> tools,
      Consumer<String> onToken) {
    HttpResponse<Stream<String>> response = send(buildRequest(messages, tools, true), HttpResponse.BodyHandlers.ofLines());
    if (response.statusCode() / 100 != 2) {
      String body;
      try (Stream<String> lines = response.body()) {
        body = lines.collect(Collectors.joining("\n"));
      }
      throw httpError(response.statusCode(), body);
    }
    return parseStream(response.body(), onToken);
  }

  private HttpRequest buildRequest(List<Llm.Message> messages, List<Llm.ToolSpec> tools, boolean stream) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", config.model());
    body.put("temperature", 0);
    if (stream) {
      body.put("stream", true);
    }

    ArrayNode messageArray = body.putArray("messages");
    messages.forEach(message -> messageArray.add(toJson(message)));

    if (!tools.isEmpty()) {
      ArrayNode toolArray = body.putArray("tools");
      for (Llm.ToolSpec spec : tools) {
        ObjectNode tool = toolArray.addObject();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", spec.name());
        function.put("description", spec.description());
        function.set("parameters", objectMapper.valueToTree(spec.parameters()));
      }
    }

    return HttpRequest.newBuilder(endpoint)
        .timeout(config.timeout())
        .header("Content-Type", "application/json")
        .header("Accept", stream ? "text/event-stream" : "application/json")
        .header("Authorization", "Bearer " + config.apiKey())
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();
  }

  private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    try {
      return httpClient.send(request, handler);
    } catch (IOException exception) {
      throw new LlmException("调用 LLM 失败：" + exception.getMessage(), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new LlmException("调用 LLM 被中断", exception);
    }
  }

  private ObjectNode toJson(Llm.Message message) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("role", message.role());
    if (message.toolCallId() != null) {
      node.put("tool_call_id", message.toolCallId());
    }
    if (message.content() == null) {
      node.putNull("content");
    } else {
      node.put("content", message.content());
    }
    if (!message.toolCalls().isEmpty()) {
      ArrayNode calls = node.putArray("tool_calls");
      for (Llm.ToolCall call : message.toolCalls()) {
        ObjectNode callNode = calls.addObject();
        callNode.put("id", call.id());
        callNode.put("type", "function");
        ObjectNode function = callNode.putObject("function");
        function.put("name", call.name());
        function.put("arguments", call.arguments());
      }
    }
    return node;
  }

  private Llm.Completion parse(String rawBody) {
    try {
      JsonNode root = objectMapper.readTree(rawBody);
      JsonNode message = root.path("choices").path(0).path("message");
      if (message.isMissingNode()) {
        throw new LlmException("LLM 响应缺少 choices[0].message：" + snippet(rawBody));
      }
      JsonNode contentNode = message.path("content");
      String content = contentNode.isMissingNode() || contentNode.isNull() ? null : contentNode.asText();
      List<Llm.ToolCall> calls = new ArrayList<>();
      for (JsonNode call : message.path("tool_calls")) {
        calls.add(new Llm.ToolCall(
            call.path("id").asText(),
            call.path("function").path("name").asText(),
            call.path("function").path("arguments").asText("{}")));
      }
      return new Llm.Completion(content, calls);
    } catch (IOException exception) {
      throw new LlmException("解析 LLM 响应失败：" + exception.getMessage(), exception);
    }
  }

  private Llm.Completion parseStream(Stream<String> lines, Consumer<String> onToken) {
    StringBuilder text = new StringBuilder();
    Map<Integer, ToolCallBuilder> toolCalls = new TreeMap<>();
    try (lines) {
      Iterator<String> iterator = lines.iterator();
      while (iterator.hasNext()) {
        String line = iterator.next().trim();
        if (line.isEmpty() || !line.startsWith("data:")) {
          continue;
        }
        String payload = line.substring("data:".length()).trim();
        if ("[DONE]".equals(payload)) {
          break;
        }
        JsonNode chunk = objectMapper.readTree(payload);
        JsonNode delta = chunk.path("choices").path(0).path("delta");
        JsonNode content = delta.path("content");
        if (!content.isMissingNode() && !content.isNull()) {
          String piece = content.asText();
          if (!piece.isEmpty()) {
            text.append(piece);
            onToken.accept(piece);
          }
        }
        for (JsonNode callNode : delta.path("tool_calls")) {
          int index = callNode.path("index").asInt(0);
          ToolCallBuilder builder = toolCalls.computeIfAbsent(index, key -> new ToolCallBuilder());
          JsonNode id = callNode.path("id");
          if (!id.isMissingNode() && !id.isNull() && !id.asText().isBlank()) {
            builder.id = id.asText();
          }
          JsonNode function = callNode.path("function");
          JsonNode name = function.path("name");
          if (!name.isMissingNode() && !name.isNull() && !name.asText().isBlank()) {
            builder.name = name.asText();
          }
          JsonNode arguments = function.path("arguments");
          if (!arguments.isMissingNode() && !arguments.isNull()) {
            builder.arguments.append(arguments.asText());
          }
        }
      }
    } catch (IOException exception) {
      throw new LlmException("读取 LLM 流失败：" + exception.getMessage(), exception);
    }

    List<Llm.ToolCall> calls = toolCalls.values().stream()
        .filter(builder -> builder.name != null && !builder.name.isBlank())
        .map(builder -> new Llm.ToolCall(
            builder.id == null || builder.id.isBlank() ? "call_" + UUID.randomUUID() : builder.id,
            builder.name,
            builder.arguments.isEmpty() ? "{}" : builder.arguments.toString()))
        .toList();
    String content = text.isEmpty() ? null : text.toString();
    return new Llm.Completion(content, calls);
  }

  private static LlmException httpError(int status, String body) {
    return new LlmException("LLM 返回 HTTP " + status + "：" + snippet(body));
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String snippet(String text) {
    return text.length() <= 500 ? text : text.substring(0, 500) + "...";
  }

  /** Accumulates streamed tool-call fragments for one {@code choices[0].delta.tool_calls[]} index. */
  private static final class ToolCallBuilder {

    private String id;
    private String name;
    private final StringBuilder arguments = new StringBuilder();
  }
}
