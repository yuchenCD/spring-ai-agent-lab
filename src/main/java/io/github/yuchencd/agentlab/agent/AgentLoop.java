package io.github.yuchencd.agentlab.agent;

import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.llm.Llm;
import io.github.yuchencd.agentlab.llm.LlmClient;
import io.github.yuchencd.agentlab.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The ReAct-style loop: ask the model, execute any requested tools, feed the
 * results back, repeat until the model answers with plain text or the step
 * budget runs out.
 *
 * <p>Every tool failure is converted to an {@code ERROR: ...} tool message so a
 * broken tool fails the turn gracefully instead of the request.</p>
 */
@Component
public class AgentLoop {

  private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

  private final LlmClient llmClient;
  private final ToolRegistry toolRegistry;
  private final int maxSteps;

  public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentProperties properties) {
    this.llmClient = llmClient;
    this.toolRegistry = toolRegistry;
    this.maxSteps = Math.max(1, properties.loop().maxSteps());
  }

  public AgentRun run(List<Llm.Message> conversation, StepListener listener) {
    List<Llm.Message> messages = new ArrayList<>(conversation);
    List<Step> steps = new ArrayList<>();

    for (int step = 1; step <= maxSteps; step++) {
      Llm.Completion completion = llmClient.complete(messages, toolRegistry.specs());
      if (!completion.hasToolCalls()) {
        String reply = completion.content() == null ? "" : completion.content();
        messages.add(Llm.Message.assistant(reply));
        return new AgentRun(reply, List.copyOf(steps), List.copyOf(messages), false);
      }

      messages.add(Llm.Message.assistant(completion.content(), completion.toolCalls()));
      for (Llm.ToolCall call : completion.toolCalls()) {
        String result;
        try {
          result = toolRegistry.execute(call.name(), call.arguments());
        } catch (RuntimeException exception) {
          result = "ERROR: " + exception.getMessage();
          log.warn("工具 {} 执行失败：{}", call.name(), exception.getMessage());
        }
        messages.add(Llm.Message.tool(call.id(), result));
        Step stepRecord = new Step(step, call.name(), call.arguments(), result);
        steps.add(stepRecord);
        listener.onStep(stepRecord);
      }
    }

    String reply = "已达到最大工具调用步数（" + maxSteps + "），已停止。请缩小问题范围或补充信息。";
    return new AgentRun(reply, List.copyOf(steps), List.copyOf(messages), true);
  }

  /** One executed tool call, surfaced to HTTP clients for observability. */
  public record Step(int step, String tool, String arguments, String result) {
  }

  /** Result of one agent turn. */
  public record AgentRun(String reply, List<Step> steps, List<Llm.Message> messages, boolean stepLimitReached) {
  }

  /** Observer invoked for each executed tool call, used by the SSE endpoint. */
  @FunctionalInterface
  public interface StepListener {

    void onStep(Step step);

    /** A listener that ignores every step. */
    StepListener NONE = step -> {
    };
  }
}
