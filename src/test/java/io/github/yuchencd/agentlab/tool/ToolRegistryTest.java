package io.github.yuchencd.agentlab.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuchencd.agentlab.llm.Llm;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  private final CalculatorTool calculator = new CalculatorTool();

  @Test
  void registersToolAndRoutesCalls() {
    ToolRegistry registry = new ToolRegistry(List.of(calculator));

    assertThat(registry.specs()).extracting(Llm.ToolSpec::name).containsExactly("calculator");
    assertThat(registry.execute("calculator", "{\"expression\":\"2+2\"}")).isEqualTo("4");
  }

  @Test
  void refusesDuplicateToolNames() {
    ToolRegistry registry = new ToolRegistry(List.of(calculator));

    assertThatThrownBy(() -> registry.register(calculator))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("重复注册");
  }

  @Test
  void refusesUnknownTool() {
    ToolRegistry registry = new ToolRegistry(List.of(calculator));

    assertThatThrownBy(() -> registry.execute("does-not-exist", "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("未知工具");
  }
}
