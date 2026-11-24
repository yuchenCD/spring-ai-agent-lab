package io.github.yuchencd.agentlab.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.yuchencd.agentlab.config.AgentProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Embeddings from any OpenAI-compatible {@code /embeddings} endpoint
 * (OpenAI, Qwen/SiliconFlow, vLLM, …).
 */
public class OpenAiEmbeddingClient implements EmbeddingClient {

  private final AgentProperties.Embedding config;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final URI endpoint;
  private final int dimensions;

  public OpenAiEmbeddingClient(AgentProperties.Embedding config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    this.endpoint = URI.create(stripTrailingSlash(config.baseUrl()) + "/embeddings");
    this.dimensions = config.dimensions();
  }

  @Override
  public float[] embed(String text) {
    return embedAll(List.of(text)).get(0);
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", config.model());
    var input = body.putArray("input");
    texts.forEach(input::add);

    HttpRequest request = HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + config.apiKey())
        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new EmbeddingException("调用 embedding 失败：" + exception.getMessage(), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new EmbeddingException("调用 embedding 被中断", exception);
    }
    if (response.statusCode() / 100 != 2) {
      throw new EmbeddingException("embedding 返回 HTTP " + response.statusCode() + "：" + snippet(response.body()));
    }
    return parse(response.body(), texts.size());
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  @Override
  public String providerName() {
    return "openai-compatible:" + config.model();
  }

  private List<float[]> parse(String rawBody, int expected) {
    try {
      JsonNode data = objectMapper.readTree(rawBody).path("data");
      List<float[]> vectors = new ArrayList<>(expected);
      for (JsonNode item : data) {
        JsonNode embedding = item.path("embedding");
        float[] vector = new float[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
          vector[index] = (float) embedding.get(index).asDouble();
        }
        vectors.add(vector);
      }
      if (vectors.size() != expected) {
        throw new EmbeddingException("embedding 返回数量不匹配：期望 " + expected + "，实际 " + vectors.size());
      }
      return List.copyOf(vectors);
    } catch (IOException exception) {
      throw new EmbeddingException("解析 embedding 响应失败：" + exception.getMessage(), exception);
    }
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String snippet(String text) {
    return text.length() <= 300 ? text : text.substring(0, 300) + "...";
  }
}
