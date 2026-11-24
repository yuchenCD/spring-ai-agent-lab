package io.github.yuchencd.agentlab.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.llm.Llm;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisChatMemoryTest {

  private static final AgentProperties PROPERTIES = new AgentProperties(
      null,
      null,
      new AgentProperties.Memory(2, "redis", Duration.ofHours(24)),
      null,
      null);

  private final Map<String, List<String>> store = new HashMap<>();
  private StringRedisTemplate redis;
  private RedisChatMemory memory;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    ListOperations<String, String> ops = mock(ListOperations.class);
    when(redis.opsForList()).thenReturn(ops);
    when(ops.rightPush(anyString(), anyString())).thenAnswer(invocation -> {
      String key = invocation.getArgument(0);
      String value = invocation.getArgument(1);
      store.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
      return (long) store.get(key).size();
    });
    when(ops.range(anyString(), anyLong(), anyLong())).thenAnswer(invocation -> {
      String key = invocation.getArgument(0);
      long start = invocation.getArgument(1);
      long end = invocation.getArgument(2);
      List<String> entries = store.getOrDefault(key, List.of());
      int from = (int) Math.max(0, start < 0 ? entries.size() + start : start);
      int to = (int) Math.min(entries.size() - 1, end < 0 ? entries.size() + end : end);
      return from > to ? List.of() : List.copyOf(entries.subList(from, to + 1));
    });
    doAnswer(invocation -> {
      String key = invocation.getArgument(0);
      long start = invocation.getArgument(1);
      long end = invocation.getArgument(2);
      List<String> entries = store.get(key);
      if (entries == null || entries.isEmpty()) {
        return null;
      }
      int from = (int) Math.max(0, start < 0 ? entries.size() + start : start);
      int to = (int) Math.min(entries.size() - 1, end < 0 ? entries.size() + end : end);
      store.put(key, new ArrayList<>(entries.subList(from, Math.max(from, to + 1))));
      return null;
    }).when(ops).trim(anyString(), anyLong(), anyLong());
    when(redis.delete(anyString())).thenAnswer(invocation -> {
      store.remove(invocation.getArgument(0));
      return true;
    });
    memory = new RedisChatMemory(redis, new ObjectMapper(), PROPERTIES);
  }

  @Test
  void storesMessagesAsJsonAndReadsThemBack() {
    memory.append("c1", Llm.Message.user("你好"), Llm.Message.assistant("你好呀"));

    assertThat(memory.get("c1")).extracting(Llm.Message::role).containsExactly("user", "assistant");
    assertThat(memory.get("c1")).extracting(Llm.Message::content).containsExactly("你好", "你好呀");
    assertThat(store.get("agentlab:memory:c1")).hasSize(2);
  }

  @Test
  void trimsToTheConfiguredBoundAndRefreshesTtl() {
    memory.append("c1", Llm.Message.user("one"));
    memory.append("c1", Llm.Message.user("two"));
    memory.append("c1", Llm.Message.user("three"));

    assertThat(memory.get("c1")).hasSize(2);
    assertThat(memory.get("c1")).extracting(Llm.Message::content).containsExactly("two", "three");
    verify(redis, org.mockito.Mockito.atLeastOnce()).expire(eq("agentlab:memory:c1"), any(Duration.class));
  }

  @Test
  void clearDeletesTheKey() {
    memory.append("c1", Llm.Message.user("hello"));

    memory.clear("c1");

    assertThat(memory.get("c1")).isEmpty();
  }
}
