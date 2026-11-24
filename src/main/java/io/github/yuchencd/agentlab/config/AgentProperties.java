package io.github.yuchencd.agentlab.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything under the {@code agent.*} configuration prefix.
 *
 * <p>Bound as a record so the configuration is immutable after startup; see
 * {@code application.yml} for defaults.</p>
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(Llm llm, Loop loop, Memory memory, Embedding embedding, Rag rag) {

  /** LLM provider configuration (OpenAI-compatible chat completions endpoint). */
  public record Llm(
      String provider,
      String baseUrl,
      String apiKey,
      String model,
      Duration timeout,
      int maxAttempts,
      Duration retryBackoff) {
  }

  /** Agent loop limits and tool execution strategy. */
  public record Loop(int maxSteps, Duration toolTimeout, boolean parallelTools) {
  }

  /**
   * Conversation memory.
   *
   * @param type {@code in-memory} (default) or {@code redis}
   * @param ttl  Redis key lifetime; ignored by the in-memory store
   */
  public record Memory(int maxMessages, String type, Duration ttl) {
  }

  /**
   * Embedding provider for retrieval. An empty api-key falls back to the
   * offline deterministic hashing embedder.
   */
  public record Embedding(String baseUrl, String apiKey, String model, int dimensions) {
  }

  /** Retrieval settings for the knowledge base. */
  public record Rag(int chunkSize, int chunkOverlap, int topK) {
  }
}
