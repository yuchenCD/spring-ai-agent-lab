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
import java.util.List;

/**
 * Calls any OpenAI-compatible {@code /chat/completions} endpoint (DeepSeek by default).
 *
 * <p>Uses the JDK {@link HttpClient} directly instead of an SDK so the wire format
 * is visible and testable — see {@code docs/adr/0001-raw-http-client.md}.</p>
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
    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", config.model());
    body.put("temperature", 0);

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

    HttpRequest request = HttpRequest.newBuilder(endpoint)
        .timeout(config.timeout())
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + config.apiKey())
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new LlmException("调用 LLM 失败：" + exception.getMessage(), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new LlmException("调用 LLM 被中断", exception);
    }

    if (response.statusCode() / 100 != 2) {
      throw new LlmException("LLM 返回 HTTP " + response.statusCode() + "：" + snippet(response.body()));
    }
    return parse(response.body());
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

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String snippet(String text) {
    return text.length() <= 500 ? text : text.substring(0, 500) + "...";
  }
}
