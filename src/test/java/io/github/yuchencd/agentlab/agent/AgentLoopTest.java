package io.github.yuchencd.agentlab.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.llm.Llm;
import io.github.yuchencd.agentlab.llm.LlmClient;
import io.github.yuchencd.agentlab.tool.CalculatorTool;
import io.github.yuchencd.agentlab.tool.ToolRegistry;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentLoopTest {

  private static final AgentProperties PROPERTIES = new AgentProperties(
      new AgentProperties.Llm("mock", "http://localhost", "", "test-model", Duration.ofSeconds(5)),
      new AgentProperties.Loop(3),
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

  private static Llm.Completion toolCallCompletion() {
    return new Llm.Completion(null, List.of(
        new Llm.ToolCall("call-x", "calculator", "{\"expression\":\"1+1\"}")));
  }

  /** Returns pre-scripted completions in order and counts provider calls. */
  private static final class ScriptedLlmClient implements LlmClient {

    private final Deque<Llm.Completion> script;
    private int calls;

    ScriptedLlmClient(Llm.Completion... completions) {
      this.script = new ArrayDeque<>(List.of(completions));
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
    public String providerName() {
      return "scripted";
    }
  }
}
