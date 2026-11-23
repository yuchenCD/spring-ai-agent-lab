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
public record AgentProperties(Llm llm, Loop loop, Memory memory) {

  /** LLM provider configuration (OpenAI-compatible chat completions endpoint). */
  public record Llm(String provider, String baseUrl, String apiKey, String model, Duration timeout) {
  }

  /** Agent loop limits. */
  public record Loop(int maxSteps) {
  }

  /** Conversation memory limits. */
  public record Memory(int maxMessages) {
  }
}
