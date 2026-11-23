package io.github.yuchencd.agentlab.llm;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline stand-in used when no API key is configured.
 *
 * <p>It keeps the whole project runnable without credentials: arithmetic
 * questions trigger a real tool call, everything else gets an honest canned
 * answer. Tests use their own scripted clients instead.</p>
 */
public class MockLlmClient implements LlmClient {

  private static final Pattern ARITHMETIC =
      Pattern.compile("[()\\d.\\s+*/\\-]{3,}");

  @Override
  public String providerName() {
    return "mock";
  }

  @Override
  public Llm.Completion complete(List<Llm.Message> messages, List<Llm.ToolSpec> tools) {
    Llm.Message last = messages.get(messages.size() - 1);
    if (Llm.ROLE_TOOL.equals(last.role())) {
      return new Llm.Completion("工具返回：" + last.content(), List.of());
    }

    String userText = messages.stream()
        .filter(message -> Llm.ROLE_USER.equals(message.role()))
        .reduce((first, second) -> second)
        .map(Llm.Message::content)
        .orElse("");

    boolean calculatorAvailable = tools.stream().anyMatch(spec -> "calculator".equals(spec.name()));
    if (calculatorAvailable) {
      Matcher matcher = ARITHMETIC.matcher(userText);
      while (matcher.find()) {
        String candidate = matcher.group().trim();
        if (looksLikeArithmetic(candidate)) {
          return new Llm.Completion(null, List.of(
              new Llm.ToolCall("mock-call-1", "calculator", "{\"expression\":\"" + candidate + "\"}")));
        }
      }
    }

    return new Llm.Completion(
        "（mock 模式）没有配置 DEEPSEEK_API_KEY，所以我只能离线回答。你刚才说：" + userText,
        List.of());
  }

  private static boolean looksLikeArithmetic(String candidate) {
    boolean hasDigit = candidate.chars().anyMatch(Character::isDigit);
    boolean hasOperator = candidate.chars()
        .anyMatch(ch -> ch == '+' || ch == '-' || ch == '*' || ch == '/');
    return hasDigit && hasOperator;
  }
}
