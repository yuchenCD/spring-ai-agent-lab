package io.github.yuchencd.agentlab.memory;

import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.llm.Llm;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Process-local conversation memory bounded per conversation. */
@Component
public class InMemoryChatMemory implements ChatMemory {

  private final Map<String, Deque<Llm.Message>> conversations = new ConcurrentHashMap<>();
  private final int maxMessages;

  public InMemoryChatMemory(AgentProperties properties) {
    this.maxMessages = Math.max(2, properties.memory().maxMessages());
  }

  @Override
  public List<Llm.Message> get(String conversationId) {
    Deque<Llm.Message> messages = conversations.get(conversationId);
    if (messages == null) {
      return List.of();
    }
    synchronized (messages) {
      return List.copyOf(messages);
    }
  }

  @Override
  public void append(String conversationId, Llm.Message... messages) {
    Deque<Llm.Message> conversation = conversations.computeIfAbsent(conversationId, key -> new ArrayDeque<>());
    synchronized (conversation) {
      for (Llm.Message message : messages) {
        conversation.addLast(message);
        while (conversation.size() > maxMessages) {
          conversation.removeFirst();
        }
      }
    }
  }

  @Override
  public void clear(String conversationId) {
    conversations.remove(conversationId);
  }
}
