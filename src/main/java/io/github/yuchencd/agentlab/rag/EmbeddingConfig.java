package io.github.yuchencd.agentlab.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuchencd.agentlab.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Uses a real embedding provider when configured, otherwise the offline hashing embedder. */
@Configuration
public class EmbeddingConfig {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

  @Bean
  EmbeddingClient embeddingClient(AgentProperties properties, ObjectMapper objectMapper) {
    AgentProperties.Embedding embedding = properties.embedding();
    if (embedding.apiKey() == null || embedding.apiKey().isBlank()) {
      log.warn("未配置 EMBEDDING_API_KEY，检索使用离线 hashing embedding（仅词面匹配，非语义）");
      return new HashingEmbeddingClient(embedding.dimensions());
    }
    log.info("embedding provider={} model={}", embedding.baseUrl(), embedding.model());
    return new OpenAiEmbeddingClient(embedding, objectMapper);
  }
}
