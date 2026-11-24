package io.github.yuchencd.agentlab.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.yuchencd.agentlab.config.AgentProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleLlmClientTest {

  private HttpServer server;
  private String lastRequestBody;
  private String lastAuthorizationHeader;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void parsesTextReply() {
    String baseUrl = startServer(200, """
        {"choices":[{"message":{"role":"assistant","content":"你好，我是 Agent"}}]}
        """);
    LlmClient client = client(baseUrl);

    Llm.Completion completion = client.complete(
        List.of(Llm.Message.user("hi")),
        List.of(new Llm.ToolSpec("calculator", "计算", Map.of("type", "object"))));

    assertThat(completion.content()).isEqualTo("你好，我是 Agent");
    assertThat(completion.hasToolCalls()).isFalse();
    assertThat(lastRequestBody).contains("\"model\":\"deepseek-chat\"", "\"tools\"", "\"messages\"");
    assertThat(lastAuthorizationHeader).isEqualTo("Bearer test-key");
  }

  @Test
  void parsesToolCalls() {
    String baseUrl = startServer(200,
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
            + "\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
            + "\"function\":{\"name\":\"calculator\",\"arguments\":\"{\\\"expression\\\":\\\"1+2\\\"}\"}}]}}]}");
    LlmClient client = client(baseUrl);

    Llm.Completion completion = client.complete(List.of(Llm.Message.user("1+2")), List.of());

    assertThat(completion.hasToolCalls()).isTrue();
    assertThat(completion.toolCalls()).hasSize(1);
    assertThat(completion.toolCalls().get(0).id()).isEqualTo("call_1");
    assertThat(completion.toolCalls().get(0).name()).isEqualTo("calculator");
    assertThat(completion.toolCalls().get(0).arguments()).isEqualTo("{\"expression\":\"1+2\"}");
    assertThat(lastRequestBody).doesNotContain("\"tools\"");
  }

  @Test
  void raisesOnHttpError() {
    String baseUrl = startServer(429, "{\"error\":\"rate limited\"}");
    LlmClient client = client(baseUrl);

    assertThatThrownBy(() -> client.complete(List.of(Llm.Message.user("hi")), List.of()))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("HTTP 429")
        .hasMessageContaining("rate limited");
  }

  @Test
  void reportsProviderName() {
    assertThat(client("http://localhost:1").providerName()).isEqualTo("deepseek");
  }

  @Test
  void streamsTextDeltas() {
    String baseUrl = startStreamServer(List.of(
        "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n",
        "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n",
        "data: [DONE]\n\n"));
    LlmClient client = client(baseUrl);
    List<String> tokens = new ArrayList<>();

    Llm.Completion completion = client.stream(List.of(Llm.Message.user("hi")), List.of(), tokens::add);

    assertThat(tokens).containsExactly("你", "好");
    assertThat(completion.content()).isEqualTo("你好");
    assertThat(completion.hasToolCalls()).isFalse();
    assertThat(lastRequestBody).contains("\"stream\":true");
  }

  @Test
  void assemblesToolCallsSplitAcrossStreamChunks() {
    String baseUrl = startStreamServer(List.of(
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\","
            + "\"type\":\"function\",\"function\":{\"name\":\"calculator\",\"arguments\":\"\"}}]}}]}\n\n",
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
            + "\"function\":{\"arguments\":\"{\\\"expression\\\":\"}}]}}]}\n\n",
        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
            + "\"function\":{\"arguments\":\"\\\"1+2\\\"}\"}}]}}]}\n\n",
        "data: [DONE]\n\n"));
    LlmClient client = client(baseUrl);
    List<String> tokens = new ArrayList<>();

    Llm.Completion completion = client.stream(List.of(Llm.Message.user("1+2")), List.of(), tokens::add);

    assertThat(tokens).isEmpty();
    assertThat(completion.hasToolCalls()).isTrue();
    assertThat(completion.toolCalls()).hasSize(1);
    assertThat(completion.toolCalls().get(0).id()).isEqualTo("call_1");
    assertThat(completion.toolCalls().get(0).name()).isEqualTo("calculator");
    assertThat(completion.toolCalls().get(0).arguments()).isEqualTo("{\"expression\":\"1+2\"}");
  }

  @Test
  void raisesOnStreamHttpError() {
    String baseUrl = startServer(500, "{\"error\":\"upstream down\"}");
    LlmClient client = client(baseUrl);

    assertThatThrownBy(() -> client.stream(List.of(Llm.Message.user("hi")), List.of(), token -> {
    }))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("HTTP 500");
  }

  private LlmClient client(String baseUrl) {
    AgentProperties.Llm config = new AgentProperties.Llm(
        "deepseek", baseUrl, "test-key", "deepseek-chat",
        Duration.ofSeconds(5), 3, Duration.ofMillis(10));
    return new OpenAiCompatibleLlmClient(config, new ObjectMapper());
  }

  private String startServer(int status, String responseBody) {
    HttpServer httpServer = newServer();
    httpServer.createContext("/chat/completions", exchange -> {
      lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      lastAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
      byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, payload.length);
      try (var output = exchange.getResponseBody()) {
        output.write(payload);
      }
    });
    httpServer.start();
    server = httpServer;
    return "http://127.0.0.1:" + httpServer.getAddress().getPort();
  }

  private String startStreamServer(List<String> chunks) {
    HttpServer httpServer = newServer();
    httpServer.createContext("/chat/completions", exchange -> {
      lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      lastAuthorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
      exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (var output = exchange.getResponseBody()) {
        for (String chunk : chunks) {
          output.write(chunk.getBytes(StandardCharsets.UTF_8));
          output.flush();
        }
      }
    });
    httpServer.start();
    server = httpServer;
    return "http://127.0.0.1:" + httpServer.getAddress().getPort();
  }

  private static HttpServer newServer() {
    try {
      return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException exception) {
      throw new IllegalStateException("cannot start test HTTP server", exception);
    }
  }
}
