package io.github.yuchencd.agentlab.llm;

import java.util.List;
import java.util.function.Consumer;

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

  /**
   * Stream the next turn: each text delta is handed to {@code onToken} as soon
   * as it arrives, and the accumulated turn is returned when the stream ends.
   *
   * <p>The default implementation asks for the whole turn and emits it as one
   * delta, which keeps simple providers and tests working unchanged.</p>
   *
   * @param messages full conversation so far, oldest first
   * @param tools    tools the model may call in this turn
   * @param onToken  receives text deltas in order
   * @return the accumulated turn, including any assembled tool calls
   */
  default Llm.Completion stream(
      List<Llm.Message> messages,
      List<Llm.ToolSpec> tools,
      Consumer<String> onToken) {
    Llm.Completion completion = complete(messages, tools);
    if (completion.content() != null && !completion.content().isEmpty()) {
      onToken.accept(completion.content());
    }
    return completion;
  }

  /** Provider name shown in API responses, for example {@code deepseek} or {@code mock}. */
  String providerName();
}
