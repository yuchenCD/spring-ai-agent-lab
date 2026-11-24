package io.github.yuchencd.agentlab.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuchencd.agentlab.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Selects the conversation memory backend from {@code agent.memory.type}. */
@Configuration
public class MemoryConfig {

  private static final Logger log = LoggerFactory.getLogger(MemoryConfig.class);

  @Bean
  ChatMemory chatMemory(
      AgentProperties properties,
      ObjectMapper objectMapper,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    if ("redis".equalsIgnoreCase(properties.memory().type())) {
      StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
      if (redisTemplate != null) {
        log.info("会话记忆使用 Redis（ttl={}，maxMessages={}）", properties.memory().ttl(), properties.memory().maxMessages());
        return new RedisChatMemory(redisTemplate, objectMapper, properties);
      }
      log.warn("agent.memory.type=redis 但未找到 StringRedisTemplate，回退到进程内记忆");
    }
    return new InMemoryChatMemory(properties);
  }
}
