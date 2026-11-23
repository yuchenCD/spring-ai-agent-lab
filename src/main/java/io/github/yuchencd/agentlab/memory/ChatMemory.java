package io.github.yuchencd.agentlab.memory;

import io.github.yuchencd.agentlab.llm.Llm;
import java.util.List;

/**
 * Conversation storage for the agent.
 *
 * <p>An interface on purpose: the in-memory implementation is enough for a demo,
 * a Redis implementation can be dropped in later without touching the agent.</p>
 */
public interface ChatMemory {

  /** Previous messages of a conversation, oldest first. */
  List<Llm.Message> get(String conversationId);

  /** Appends messages, trimming the oldest ones when the configured bound is exceeded. */
  void append(String conversationId, Llm.Message... messages);

  /** Forgets one conversation. */
  void clear(String conversationId);
}
