package io.github.yuchencd.agentlab.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.llm.Llm;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed conversation memory: one list per conversation, trimmed to the
 * configured bound and expired after a TTL.
 *
 * <p>Messages are stored as JSON so the log stays debuggable with
 * {@code redis-cli LRANGE agentlab:memory:&lt;id&gt; 0 -1}.</p>
 */
public class RedisChatMemory implements ChatMemory {

  private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);
  private static final String KEY_PREFIX = "agentlab:memory:";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final int maxMessages;
  private final Duration ttl;

  public RedisChatMemory(StringRedisTemplate redis, ObjectMapper objectMapper, AgentProperties properties) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.maxMessages = Math.max(2, properties.memory().maxMessages());
    this.ttl = properties.memory().ttl();
  }

  @Override
  public List<Llm.Message> get(String conversationId) {
    List<String> entries = redis.opsForList().range(key(conversationId), 0, -1);
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    List<Llm.Message> messages = new ArrayList<>(entries.size());
    for (String entry : entries) {
      try {
        messages.add(objectMapper.readValue(entry, Llm.Message.class));
      } catch (JsonProcessingException exception) {
        log.warn("跳过无法解析的历史消息（conversation={}）：{}", conversationId, exception.getMessage());
      }
    }
    return List.copyOf(messages);
  }

  @Override
  public void append(String conversationId, Llm.Message... messages) {
    String key = key(conversationId);
    for (Llm.Message message : messages) {
      redis.opsForList().rightPush(key, serialise(message));
    }
    redis.opsForList().trim(key, -maxMessages, -1);
    if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
      redis.expire(key, ttl);
    }
  }

  @Override
  public void clear(String conversationId) {
    redis.delete(key(conversationId));
  }

  private String serialise(Llm.Message message) {
    try {
      return objectMapper.writeValueAsString(message);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法序列化会话消息", exception);
    }
  }

  private static String key(String conversationId) {
    return KEY_PREFIX + conversationId;
  }
}
