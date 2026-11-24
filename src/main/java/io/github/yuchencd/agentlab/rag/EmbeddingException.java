package io.github.yuchencd.agentlab.rag;

/** Raised when an embedding provider cannot be reached or answers abnormally. */
public class EmbeddingException extends RuntimeException {

  public EmbeddingException(String message) {
    super(message);
  }

  public EmbeddingException(String message, Throwable cause) {
    super(message, cause);
  }
}
