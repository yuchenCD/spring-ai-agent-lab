package io.github.yuchencd.agentlab.web;

import java.time.Instant;
import org.springframework.http.HttpStatus;

/** Uniform error body for every non-2xx response. */
public record ApiError(int status, String code, String message, Instant timestamp) {

  public static ApiError of(HttpStatus status, String code, String message) {
    return new ApiError(status.value(), code, message, Instant.now());
  }
}
