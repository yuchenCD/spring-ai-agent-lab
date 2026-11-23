package io.github.yuchencd.agentlab.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculatorToolTest {

  private final CalculatorTool tool = new CalculatorTool();

  @ParameterizedTest
  @CsvSource({
      "'1+2*3', 7",
      "'(1+2)*3', 9",
      "'10/4', 2.5",
      "'-3*(2+1)', -9",
      "'1.5+2.25', 3.75",
      "' 2 * ( 3 + 4 ) ', 14"
  })
  void evaluatesExpressions(String expression, String expected) {
    assertThat(tool.execute("{\"expression\":\"" + expression + "\"}")).isEqualTo(expected);
  }

  @Test
  void rejectsDivisionByZero() {
    assertThatThrownBy(() -> tool.execute("{\"expression\":\"1/0\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("除数不能为 0");
  }

  @Test
  void rejectsMissingExpression() {
    assertThatThrownBy(() -> tool.execute("{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expression");
  }

  @Test
  void rejectsTrailingGarbage() {
    assertThatThrownBy(() -> tool.execute("{\"expression\":\"1+2abc\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("多余字符");
  }

  @Test
  void exposesStableNameAndSchema() {
    assertThat(tool.name()).isEqualTo("calculator");
    assertThat(tool.description()).contains("算术");
    assertThat(tool.parameters()).containsKey("properties");
  }
}
