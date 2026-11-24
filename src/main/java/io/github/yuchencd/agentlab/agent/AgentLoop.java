package io.github.yuchencd.agentlab.agent;

import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.llm.Llm;
import io.github.yuchencd.agentlab.llm.LlmClient;
import io.github.yuchencd.agentlab.tool.ToolRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The ReAct-style loop: ask the model, execute any requested tools, feed the
 * results back, repeat until the model answers with plain text or the step
 * budget runs out.
 *
 * <p>v0.2 additions: the model turn is streamed (text deltas are forwarded to
 * the caller as they arrive), and independent tool calls from one assistant
 * turn run concurrently on virtual threads under a bounded deadline. Tool
 * failures are converted to {@code ERROR: ...} observations so a broken tool
 * fails the turn gracefully instead of the request.</p>
 */
@Component
public class AgentLoop {

  private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);
  private static final Duration DEFAULT_TOOL_TIMEOUT = Duration.ofSeconds(10);

  private final LlmClient llmClient;
  private final ToolRegistry toolRegistry;
  private final int maxSteps;
  private final Duration toolTimeout;
  private final boolean parallelTools;
  private final ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, AgentProperties properties) {
    this.llmClient = llmClient;
    this.toolRegistry = toolRegistry;
    this.maxSteps = Math.max(1, properties.loop().maxSteps());
    this.toolTimeout = properties.loop().toolTimeout() == null ? DEFAULT_TOOL_TIMEOUT : properties.loop().toolTimeout();
    this.parallelTools = properties.loop().parallelTools();
  }

  /** Runs a turn without streaming text deltas. */
  public AgentRun run(List<Llm.Message> conversation, StepListener listener) {
    return run(conversation, token -> {
    }, listener);
  }

  /**
   * Runs one turn.
   *
   * @param conversation full conversation including the system prompt
   * @param onToken      receives streamed answer text as it is generated
   * @param listener     receives one event per executed tool call
   * @return the final reply plus every executed step and the full message list
   */
  public AgentRun run(List<Llm.Message> conversation, Consumer<String> onToken, StepListener listener) {
    List<Llm.Message> messages = new ArrayList<>(conversation);
    List<Step> steps = new ArrayList<>();

    for (int step = 1; step <= maxSteps; step++) {
      Llm.Completion completion = llmClient.stream(messages, toolRegistry.specs(), onToken);
      if (!completion.hasToolCalls()) {
        String reply = completion.content() == null ? "" : completion.content();
        messages.add(Llm.Message.assistant(reply));
        return new AgentRun(reply, List.copyOf(steps), List.copyOf(messages), false);
      }

      messages.add(Llm.Message.assistant(completion.content(), completion.toolCalls()));
      List<Step> executed = executeTools(step, completion.toolCalls());
      for (int index = 0; index < executed.size(); index++) {
        Step stepRecord = executed.get(index);
        messages.add(Llm.Message.tool(completion.toolCalls().get(index).id(), stepRecord.result()));
        steps.add(stepRecord);
        listener.onStep(stepRecord);
      }
    }

    String reply = "已达到最大工具调用步数（" + maxSteps + "），已停止。请缩小问题范围或补充信息。";
    return new AgentRun(reply, List.copyOf(steps), List.copyOf(messages), true);
  }

  private List<Step> executeTools(int stepNumber, List<Llm.ToolCall> calls) {
    if (!parallelTools || calls.size() == 1) {
      List<Step> executed = new ArrayList<>(calls.size());
      for (Llm.ToolCall call : calls) {
        executed.add(executeTool(stepNumber, call));
      }
      return executed;
    }

    List<Callable<Step>> tasks = calls.stream()
        .map(call -> (Callable<Step>) () -> executeTool(stepNumber, call))
        .toList();
    try {
      long deadlineMillis = Math.max(1L, toolTimeout.toMillis());
      List<Future<Step>> futures = toolExecutor.invokeAll(tasks, deadlineMillis, TimeUnit.MILLISECONDS);
      List<Step> executed = new ArrayList<>(calls.size());
      for (int index = 0; index < futures.size(); index++) {
        Llm.ToolCall call = calls.get(index);
        Future<Step> future = futures.get(index);
        if (future.isCancelled()) {
          executed.add(new Step(stepNumber, call.name(), call.arguments(),
              "ERROR: 工具执行超时（本批上限 " + deadlineMillis + "ms）"));
          continue;
        }
        try {
          executed.add(future.get());
        } catch (ExecutionException exception) {
          Throwable cause = exception.getCause() == null ? exception : exception.getCause();
          executed.add(new Step(stepNumber, call.name(), call.arguments(), "ERROR: " + cause.getMessage()));
        }
      }
      return executed;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("工具执行被中断", exception);
    }
  }

  private Step executeTool(int stepNumber, Llm.ToolCall call) {
    String result;
    try {
      result = toolRegistry.execute(call.name(), call.arguments());
    } catch (RuntimeException exception) {
      result = "ERROR: " + exception.getMessage();
      log.warn("工具 {} 执行失败：{}", call.name(), exception.getMessage());
    }
    return new Step(stepNumber, call.name(), call.arguments(), result);
  }

  @PreDestroy
  void shutdownExecutor() {
    toolExecutor.shutdownNow();
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
