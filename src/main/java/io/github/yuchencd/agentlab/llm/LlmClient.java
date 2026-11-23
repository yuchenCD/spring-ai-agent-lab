package io.github.yuchencd.agentlab.llm;

import java.util.List;

/** The one capability the agent loop needs from an LLM provider. */
public interface LlmClient {

  /**
   * Ask the model for the next turn.
   *
   * @param messages full conversation so far, oldest first
   * @param tools    tools the model may call in this turn
   * @return final text, or tool calls to execute
   */
  Llm.Completion complete(List<Llm.Message> messages, List<Llm.ToolSpec> tools);

  /** Provider name shown in API responses, for example {@code deepseek} or {@code mock}. */
  String providerName();
}
