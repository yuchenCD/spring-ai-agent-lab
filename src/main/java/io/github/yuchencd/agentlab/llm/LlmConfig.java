package io.github.yuchencd.agentlab.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuchencd.agentlab.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the real provider when an API key exists, otherwise the offline mock, wrapped with retries. */
@Configuration
public class LlmConfig {

  private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

  @Bean
  LlmClient llmClient(AgentProperties properties, ObjectMapper objectMapper) {
    AgentProperties.Llm llm = properties.llm();
    LlmClient base;
    if ("mock".equalsIgnoreCase(llm.provider()) || llm.apiKey() == null || llm.apiKey().isBlank()) {
      log.warn("未配置 DEEPSEEK_API_KEY（或 provider=mock），启用内置 MockLlmClient 离线演示模式");
      base = new MockLlmClient();
    } else {
      log.info("LLM provider={} model={} endpoint={} maxAttempts={}", llm.provider(), llm.model(), llm.baseUrl(), llm.maxAttempts());
      base = new OpenAiCompatibleLlmClient(llm, objectMapper);
    }
    return new RetryingLlmClient(base, llm.maxAttempts(), llm.retryBackoff());
  }
}
