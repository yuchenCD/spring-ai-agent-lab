package io.github.yuchencd.agentlab.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Evaluates arithmetic expressions with a small recursive-descent parser.
 *
 * <p>A parser (instead of a script engine or {@code eval}) keeps the tool
 * sandboxed: only numbers, whitespace, {@code + - * /} and parentheses are
 * accepted.</p>
 */
@Component
public class CalculatorTool implements Tool {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  public String name() {
    return "calculator";
  }

  @Override
  public String description() {
    return "计算算术表达式，支持 + - * / 和括号，例如 (1+2)*3。只做精确的数值计算，不执行其他代码。";
  }

  @Override
  public Map<String, Object> parameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of(
            "expression", Map.of(
                "type", "string",
                "description", "要计算的算术表达式，例如 (1+2)*3")),
        "required", List.of("expression"));
  }

  @Override
  public String execute(String argumentsJson) {
    JsonNode arguments;
    try {
      arguments = JSON.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
    } catch (Exception exception) {
      throw new IllegalArgumentException("arguments 不是合法 JSON：" + exception.getMessage());
    }
    String expression = arguments.path("expression").asText("");
    if (expression.isBlank()) {
      throw new IllegalArgumentException("缺少 expression 参数");
    }
    return format(new ExpressionParser(expression).parse());
  }

  private static String format(double value) {
    if (!Double.isFinite(value)) {
      throw new ArithmeticException("计算结果不是有限数值");
    }
    if (value == Math.rint(value) && Math.abs(value) < 1e15) {
      return Long.toString((long) value);
    }
    return Double.toString(value);
  }

  /** expression := term (('+' | '-') term)*；term := unary (('*' | '/') unary)*；unary := [+-]? primary。 */
  static final class ExpressionParser {

    private final String input;
    private int position;

    ExpressionParser(String input) {
      this.input = input;
    }

    double parse() {
      double value = expression();
      skipWhitespace();
      if (position != input.length()) {
        throw error("多余字符 '" + input.charAt(position) + "'");
      }
      return value;
    }

    private double expression() {
      double value = term();
      while (true) {
        skipWhitespace();
        if (consume('+')) {
          value += term();
        } else if (consume('-')) {
          value -= term();
        } else {
          return value;
        }
      }
    }

    private double term() {
      double value = unary();
      while (true) {
        skipWhitespace();
        if (consume('*')) {
          value *= unary();
        } else if (consume('/')) {
          double divisor = unary();
          if (divisor == 0) {
            throw error("除数不能为 0");
          }
          value /= divisor;
        } else {
          return value;
        }
      }
    }

    private double unary() {
      skipWhitespace();
      if (consume('-')) {
        return -unary();
      }
      if (consume('+')) {
        return unary();
      }
      return primary();
    }

    private double primary() {
      skipWhitespace();
      if (consume('(')) {
        double value = expression();
        skipWhitespace();
        if (!consume(')')) {
          throw error("缺少右括号");
        }
        return value;
      }
      int start = position;
      while (position < input.length()
          && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.')) {
        position++;
      }
      if (start == position) {
        throw error("需要一个数字");
      }
      String number = input.substring(start, position);
      try {
        return Double.parseDouble(number);
      } catch (NumberFormatException exception) {
        throw error("非法数字 '" + number + "'");
      }
    }

    private boolean consume(char expected) {
      if (position < input.length() && input.charAt(position) == expected) {
        position++;
        return true;
      }
      return false;
    }

    private void skipWhitespace() {
      while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
        position++;
      }
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException("表达式错误（位置 " + (position + 1) + "）：" + message);
    }
  }
}
