package io.github.yuchencd.agentlab.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class RetryingLlmClientTest {

  @Test
  void retriesTransientFailuresThenSucceeds() {
    FlakyLlmClient flaky = new FlakyLlmClient(2, new Llm.Completion("ok", List.of()));
    RetryingLlmClient client = new RetryingLlmClient(flaky, 3, Duration.ofMillis(1));

    Llm.Completion completion = client.complete(List.of(Llm.Message.user("hi")), List.of());

    assertThat(completion.content()).isEqualTo("ok");
    assertThat(flaky.calls).isEqualTo(3);
  }

  @Test
  void doesNotRetryClientErrors() {
    FlakyLlmClient flaky = new FlakyLlmClient(Integer.MAX_VALUE, new Llm.Completion("ok", List.of()));
    flaky.failure = "LLM 返回 HTTP 400：bad request";
    RetryingLlmClient client = new RetryingLlmClient(flaky, 3, Duration.ofMillis(1));

    assertThatThrownBy(() -> client.complete(List.of(Llm.Message.user("hi")), List.of()))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("HTTP 400");
    assertThat(flaky.calls).isEqualTo(1);
  }

  @Test
  void givesUpAfterMaximumAttempts() {
    FlakyLlmClient flaky = new FlakyLlmClient(Integer.MAX_VALUE, new Llm.Completion("ok", List.of()));
    flaky.failure = "LLM 返回 HTTP 503：busy";
    RetryingLlmClient client = new RetryingLlmClient(flaky, 3, Duration.ofMillis(1));

    assertThatThrownBy(() -> client.complete(List.of(Llm.Message.user("hi")), List.of()))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("HTTP 503");
    assertThat(flaky.calls).isEqualTo(3);
  }

  @Test
  void doesNotRetryAStreamThatAlreadyEmittedTokens() {
    FlakyLlmClient flaky = new FlakyLlmClient(Integer.MAX_VALUE, new Llm.Completion("ok", List.of()));
    flaky.failDuringStream = true;
    RetryingLlmClient client = new RetryingLlmClient(flaky, 3, Duration.ofMillis(1));
    List<String> tokens = new ArrayList<>();

    assertThatThrownBy(() -> client.stream(List.of(Llm.Message.user("hi")), List.of(), tokens::add))
        .isInstanceOf(LlmException.class);
    assertThat(flaky.calls).isEqualTo(1);
    assertThat(tokens).containsExactly("partial");
  }

  private static final class FlakyLlmClient implements LlmClient {

    private final Llm.Completion success;
    private int calls;
    private int failuresRemaining;
    private String failure = "LLM 返回 HTTP 503：busy";
    private boolean failDuringStream;

    FlakyLlmClient(int failures, Llm.Completion success) {
      this.failuresRemaining = failures;
      this.success = success;
    }

    @Override
    public Llm.Completion complete(List<Llm.Message> messages, List<Llm.ToolSpec> tools) {
      calls++;
      if (failuresRemaining > 0) {
        failuresRemaining--;
        throw new LlmException(failure);
      }
      return success;
    }

    @Override
    public Llm.Completion stream(
        List<Llm.Message> messages,
        List<Llm.ToolSpec> tools,
        Consumer<String> onToken) {
      calls++;
      if (failDuringStream) {
        onToken.accept("partial");
        throw new LlmException("读取 LLM 流失败：connection reset");
      }
      if (failuresRemaining > 0) {
        failuresRemaining--;
        throw new LlmException(failure);
      }
      return success;
    }

    @Override
    public String providerName() {
      return "flaky";
    }
  }
}
