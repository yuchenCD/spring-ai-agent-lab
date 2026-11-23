package io.github.yuchencd.agentlab.llm;

/** Raised when the LLM provider cannot be reached, answers abnormally, or returns an unparsable body. */
public class LlmException extends RuntimeException {

  public LlmException(String message) {
    super(message);
  }

  public LlmException(String message, Throwable cause) {
    super(message, cause);
  }
}
