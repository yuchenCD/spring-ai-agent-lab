package io.github.yuchencd.agentlab.llm;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries transient LLM failures with exponential backoff and jitter.
 *
 * <p>Only failures that a retry can fix are retried: connection errors and
 * HTTP 429/5xx. Client errors (4xx other than 429) are surfaced immediately.
 * A stream that already emitted tokens is never retried, otherwise the client
 * would see duplicated text.</p>
 */
public class RetryingLlmClient implements LlmClient {

  private static final Logger log = LoggerFactory.getLogger(RetryingLlmClient.class);

  private final LlmClient delegate;
  private final int maxAttempts;
  private final Duration backoff;

  public RetryingLlmClient(LlmClient delegate, int maxAttempts, Duration backoff) {
    this.delegate = delegate;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.backoff = backoff == null ? Duration.ZERO : backoff;
  }

  @Override
  public String providerName() {
    return delegate.providerName();
  }

  @Override
  public Llm.Completion complete(List<Llm.Message> messages, List<Llm.ToolSpec> tools) {
    LlmException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return delegate.complete(messages, tools);
      } catch (LlmException exception) {
        last = exception;
        if (!retryable(exception) || attempt == maxAttempts) {
          throw exception;
        }
        log.warn("LLM 调用失败（第 {}/{} 次尝试），准备重试：{}", attempt, maxAttempts, exception.getMessage());
        sleep(attempt);
      }
    }
    throw last;
  }

  @Override
  public Llm.Completion stream(
      List<Llm.Message> messages,
      List<Llm.ToolSpec> tools,
      Consumer<String> onToken) {
    AtomicBoolean emitted = new AtomicBoolean(false);
    Consumer<String> guarded = token -> {
      emitted.set(true);
      onToken.accept(token);
    };
    LlmException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return delegate.stream(messages, tools, guarded);
      } catch (LlmException exception) {
        last = exception;
        if (emitted.get() || !retryable(exception) || attempt == maxAttempts) {
          throw exception;
        }
        log.warn("LLM 流式调用失败（第 {}/{} 次尝试），准备重试：{}", attempt, maxAttempts, exception.getMessage());
        sleep(attempt);
      }
    }
    throw last;
  }

  private static boolean retryable(LlmException exception) {
    String message = exception.getMessage();
    if (message == null) {
      return false;
    }
    return message.contains("HTTP 429")
        || message.contains("HTTP 500")
        || message.contains("HTTP 502")
        || message.contains("HTTP 503")
        || message.contains("HTTP 504")
        || message.contains("调用 LLM 失败");
  }

  private void sleep(int attempt) {
    long base = backoff.toMillis() * (1L << (attempt - 1));
    long jitter = base == 0 ? 0 : ThreadLocalRandom.current().nextLong(base / 2 + 1);
    long delay = base + jitter;
    if (delay <= 0) {
      return;
    }
    try {
      Thread.sleep(delay);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new LlmException("重试等待被中断", exception);
    }
  }
}
