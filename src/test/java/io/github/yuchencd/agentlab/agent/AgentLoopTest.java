package io.github.yuchencd.agentlab.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.llm.Llm;
import io.github.yuchencd.agentlab.llm.LlmClient;
import io.github.yuchencd.agentlab.tool.CalculatorTool;
import io.github.yuchencd.agentlab.tool.Tool;
import io.github.yuchencd.agentlab.tool.ToolRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentLoopTest {

  private static final AgentProperties PROPERTIES = new AgentProperties(
      new AgentProperties.Llm("mock", "http://localhost", "", "test-model", Duration.ofSeconds(5), 3, Duration.ofMillis(10)),
      new AgentProperties.Loop(3, Duration.ofSeconds(2), true),
      new AgentProperties.Memory(10));

  private final ToolRegistry registry = new ToolRegistry(List.of(new CalculatorTool()));

  @Test
  void executesToolThenReturnsFinalAnswer() {
    ScriptedLlmClient llm = new ScriptedLlmClient(
        new Llm.Completion(null, List.of(
            new Llm.ToolCall("call-1", "calculator", "{\"expression\":\"2+3\"}"))),
        new Llm.Completion("计算结果是 5", List.of()));
    AgentLoop loop = new AgentLoop(llm, registry, PROPERTIES);
    List<AgentLoop.Step> observed = new ArrayList<>();

    AgentLoop.AgentRun run = loop.run(List.of(Llm.Message.user("2+3 等于多少？")), observed::add);

    assertThat(run.reply()).isEqualTo("计算结果是 5");
    assertThat(run.stepLimitReached()).isFalse();
    assertThat(run.steps()).hasSize(1);
    assertThat(run.steps().get(0).tool()).isEqualTo("calculator");
    assertThat(run.steps().get(0).result()).isEqualTo("5");
    assertThat(observed).hasSize(1);
    assertThat(llm.calls).isEqualTo(2);
  }

  @Test
  void convertsToolFailureIntoErrorObservation() {
    ScriptedLlmClient llm = new ScriptedLlmClient(
        new Llm.Completion(null, List.of(
            new Llm.ToolCall("call-1", "calculator", "{\"expression\":\"1/0\"}"))),
        new Llm.Completion("除零错误，无法计算", List.of()));
    AgentLoop loop = new AgentLoop(llm, registry, PROPERTIES);

    AgentLoop.AgentRun run = loop.run(List.of(Llm.Message.user("1/0")), AgentLoop.StepListener.NONE);

    assertThat(run.steps()).hasSize(1);
    assertThat(run.steps().get(0).result()).startsWith("ERROR:");
  }

  @Test
  void stopsAtMaximumStepBudget() {
    ScriptedLlmClient llm = new ScriptedLlmClient(
        toolCallCompletion(), toolCallCompletion(), toolCallCompletion(), toolCallCompletion());
    AgentLoop loop = new AgentLoop(llm, registry, PROPERTIES);

    AgentLoop.AgentRun run = loop.run(List.of(Llm.Message.user("keep calling tools")), AgentLoop.StepListener.NONE);

    assertThat(run.stepLimitReached()).isTrue();
    assertThat(llm.calls).isEqualTo(3);
    assertThat(run.steps()).hasSize(3);
    assertThat(run.reply()).contains("最大工具调用步数");
  }

  @Test
  void forwardsStreamedTokens() {
    ScriptedLlmClient llm = new ScriptedLlmClient(
        List.of("计算", "结果", "是 5"),
        new Llm.Completion("计算结果是 5", List.of()));
    AgentLoop loop = new AgentLoop(llm, registry, PROPERTIES);
    List<String> tokens = new ArrayList<>();

    AgentLoop.AgentRun run = loop.run(
        List.of(Llm.Message.user("2+3 等于多少？")), tokens::add, AgentLoop.StepListener.NONE);

    assertThat(tokens).containsExactly("计算", "结果", "是 5");
    assertThat(run.reply()).isEqualTo("计算结果是 5");
  }

  @Test
  void runsIndependentToolCallsInParallelAndKeepsOrder() {
    ToolRegistry slowRegistry = new ToolRegistry(List.of(
        new SleepTool("sleep-a", 400), new SleepTool("sleep-b", 400)));
    ScriptedLlmClient llm = new ScriptedLlmClient(
        new Llm.Completion(null, List.of(
            new Llm.ToolCall("call-a", "sleep-a", "{}"),
            new Llm.ToolCall("call-b", "sleep-b", "{}"))),
        new Llm.Completion("done", List.of()));
    AgentLoop loop = new AgentLoop(llm, slowRegistry, PROPERTIES);

    long startedAt = System.nanoTime();
    AgentLoop.AgentRun run = loop.run(List.of(Llm.Message.user("run both")), AgentLoop.StepListener.NONE);
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

    assertThat(run.steps()).extracting(AgentLoop.Step::tool).containsExactly("sleep-a", "sleep-b");
    assertThat(run.steps()).extracting(AgentLoop.Step::result).containsExactly("sleep-a-done", "sleep-b-done");
    // Sequential execution would take >= 800ms; parallel execution should stay close to 400ms.
    assertThat(elapsedMillis).isLessThan(700);
  }

  @Test
  void reportsTimeoutForSlowTools() {
    AgentProperties shortTimeout = new AgentProperties(
        PROPERTIES.llm(),
        new AgentProperties.Loop(3, Duration.ofMillis(100), true),
        PROPERTIES.memory());
    ToolRegistry slowRegistry = new ToolRegistry(List.of(
        new SleepTool("sleep-a", 500), new SleepTool("sleep-b", 500)));
    ScriptedLlmClient llm = new ScriptedLlmClient(
        new Llm.Completion(null, List.of(
            new Llm.ToolCall("call-a", "sleep-a", "{}"),
            new Llm.ToolCall("call-b", "sleep-b", "{}"))),
        new Llm.Completion("done", List.of()));
    AgentLoop loop = new AgentLoop(llm, slowRegistry, shortTimeout);

    AgentLoop.AgentRun run = loop.run(List.of(Llm.Message.user("run both")), AgentLoop.StepListener.NONE);

    assertThat(run.steps()).hasSize(2);
    assertThat(run.steps().get(0).result()).contains("超时");
    assertThat(run.steps().get(1).result()).contains("超时");
  }

  private static Llm.Completion toolCallCompletion() {
    return new Llm.Completion(null, List.of(
        new Llm.ToolCall("call-x", "calculator", "{\"expression\":\"1+1\"}")));
  }

  /** A tool that sleeps, used to prove concurrent execution and timeouts. */
  private static final class SleepTool implements Tool {

    private final String name;
    private final long sleepMillis;

    SleepTool(String name, long sleepMillis) {
      this.name = name;
      this.sleepMillis = sleepMillis;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String description() {
      return "sleeps for " + sleepMillis + "ms";
    }

    @Override
    public Map<String, Object> parameters() {
      return Map.of("type", "object");
    }

    @Override
    public String execute(String argumentsJson) {
      try {
        Thread.sleep(sleepMillis);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted", exception);
      }
      return name + "-done";
    }
  }

  /** Returns pre-scripted completions in order and counts provider calls. */
  private static final class ScriptedLlmClient implements LlmClient {

    private final Deque<Llm.Completion> script;
    private final List<String> streamedTokens;
    private int calls;

    ScriptedLlmClient(Llm.Completion... completions) {
      this(List.of(), completions);
    }

    ScriptedLlmClient(List<String> streamedTokens, Llm.Completion... completions) {
      this.script = new ArrayDeque<>(List.of(completions));
      this.streamedTokens = List.copyOf(streamedTokens);
    }

    @Override
    public Llm.Completion complete(List<Llm.Message> messages, List<Llm.ToolSpec> tools) {
      calls++;
      Llm.Completion next = script.pollFirst();
      if (next == null) {
        throw new IllegalStateException("scripted client ran out of responses");
      }
      return next;
    }

    @Override
    public Llm.Completion stream(
        List<Llm.Message> messages,
        List<Llm.ToolSpec> tools,
        java.util.function.Consumer<String> onToken) {
      streamedTokens.forEach(onToken);
      return complete(messages, tools);
    }

    @Override
    public String providerName() {
      return "scripted";
    }
  }
}
