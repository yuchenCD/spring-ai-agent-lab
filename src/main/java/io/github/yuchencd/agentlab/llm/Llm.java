package io.github.yuchencd.agentlab.llm;

import java.util.List;
import java.util.Map;

/**
 * Provider-neutral vocabulary for the agent loop.
 *
 * <p>Deliberately small: the loop only needs messages, tool calls, and tool
 * specs. Keeping the vocabulary provider-neutral means a new LLM backend is a
 * single {@link LlmClient} implementation, not a change to the agent loop.</p>
 */
public final class Llm {

  public static final String ROLE_SYSTEM = "system";
  public static final String ROLE_USER = "user";
  public static final String ROLE_ASSISTANT = "assistant";
  public static final String ROLE_TOOL = "tool";

  private Llm() {
  }

  /**
   * One chat message in the OpenAI-compatible wire format.
   *
   * @param role       one of the {@code ROLE_*} constants
   * @param content    message text, may be {@code null} for an assistant turn that only calls tools
   * @param toolCalls  tool calls requested by the assistant, never {@code null}
   * @param toolCallId id of the tool call a {@code tool} message answers, otherwise {@code null}
   */
  public record Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

    public Message {
      toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message system(String content) {
      return new Message(ROLE_SYSTEM, content, List.of(), null);
    }

    public static Message user(String content) {
      return new Message(ROLE_USER, content, List.of(), null);
    }

    public static Message assistant(String content) {
      return new Message(ROLE_ASSISTANT, content, List.of(), null);
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
      return new Message(ROLE_ASSISTANT, content, toolCalls, null);
    }

    public static Message tool(String toolCallId, String content) {
      return new Message(ROLE_TOOL, content, List.of(), toolCallId);
    }
  }

  /** One tool invocation requested by the model. {@code arguments} is a JSON object string. */
  public record ToolCall(String id, String name, String arguments) {
  }

  /** A tool advertised to the model: name, description, and a JSON Schema for its arguments. */
  public record ToolSpec(String name, String description, Map<String, Object> parameters) {
  }

  /** A model turn: either final text, or one or more tool calls to execute next. */
  public record Completion(String content, List<ToolCall> toolCalls) {

    public Completion {
      toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
      return !toolCalls.isEmpty();
    }
  }
}
