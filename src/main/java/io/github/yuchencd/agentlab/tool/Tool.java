package io.github.yuchencd.agentlab.tool;

import java.util.Map;

/**
 * One capability the model can call.
 *
 * <p>Implementations are Spring beans; {@link ToolRegistry} discovers them and
 * advertises their JSON Schema to the model. Arguments arrive as the raw JSON
 * string produced by the model, so a tool owns its own parsing and validation.</p>
 */
public interface Tool {

  /** Stable name the model uses in {@code tool_calls[].function.name}. */
  String name();

  /** Human-readable description sent to the model. */
  String description();

  /** JSON Schema (draft-style, OpenAI subset) for the arguments object. */
  Map<String, Object> parameters();

  /**
   * Execute one call.
   *
   * @param argumentsJson raw JSON object string, for example {@code {"expression":"1+2"}}
   * @return a plain-text result for the model
   */
  String execute(String argumentsJson);
}
