package io.github.yuchencd.agentlab.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockLlmClientTest {

  private final MockLlmClient client = new MockLlmClient();
  private final List<Llm.ToolSpec> tools =
      List.of(new Llm.ToolSpec("calculator", "计算", Map.of("type", "object")));

  @Test
  void callsCalculatorForArithmeticText() {
    Llm.Completion completion = client.complete(
        List.of(Llm.Message.user("帮我算一下 (12+8)*3 是多少？")), tools);

    assertThat(completion.hasToolCalls()).isTrue();
    assertThat(completion.toolCalls().get(0).name()).isEqualTo("calculator");
    assertThat(completion.toolCalls().get(0).arguments()).isEqualTo("{\"expression\":\"(12+8)*3\"}");
  }

  @Test
  void callsKnowledgeSearchForKnowledgeQuestions() {
    List<Llm.ToolSpec> available = List.of(
        new Llm.ToolSpec("knowledge_search", "检索", Map.of("type", "object")),
        new Llm.ToolSpec("calculator", "计算", Map.of("type", "object")));

    Llm.Completion completion = client.complete(List.of(Llm.Message.user("帮我查一下项目资料")), available);

    assertThat(completion.hasToolCalls()).isTrue();
    assertThat(completion.toolCalls().get(0).name()).isEqualTo("knowledge_search");
    assertThat(completion.toolCalls().get(0).arguments()).contains("项目资料");
  }

  @Test
  void fallsBackToTextForNonArithmeticText() {
    Llm.Completion completion = client.complete(
        List.of(Llm.Message.user("今天适合写代码吗？")), tools);

    assertThat(completion.hasToolCalls()).isFalse();
    assertThat(completion.content()).contains("mock 模式");
  }

  @Test
  void answersWithToolResultWhenLastMessageIsTool() {
    Llm.Completion completion = client.complete(
        List.of(
            Llm.Message.user("1+2"),
            Llm.Message.assistant(null, List.of(new Llm.ToolCall("id-1", "calculator", "{}"))),
            Llm.Message.tool("id-1", "3")),
        tools);

    assertThat(completion.content()).isEqualTo("工具返回：3");
  }
}
