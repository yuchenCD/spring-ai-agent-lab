package io.github.yuchencd.agentlab.web;

import io.github.yuchencd.agentlab.llm.LlmException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps every failure to the uniform {@link ApiError} body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleValidation(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.joining("; "));
    return ApiError.of(HttpStatus.BAD_REQUEST, "validation_error",
        message.isBlank() ? "请求参数不合法" : message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleUnreadableBody(HttpMessageNotReadableException exception) {
    return ApiError.of(HttpStatus.BAD_REQUEST, "invalid_json", "请求体不是合法 JSON");
  }

  @ExceptionHandler(LlmException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  public ApiError handleLlmFailure(LlmException exception) {
    log.warn("LLM 调用失败：{}", exception.getMessage());
    return ApiError.of(HttpStatus.BAD_GATEWAY, "llm_error", exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleIllegalArgument(IllegalArgumentException exception) {
    return ApiError.of(HttpStatus.BAD_REQUEST, "bad_request", exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiError handleUnexpected(Exception exception) {
    log.error("未处理的异常", exception);
    return ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "服务器内部错误");
  }
}
