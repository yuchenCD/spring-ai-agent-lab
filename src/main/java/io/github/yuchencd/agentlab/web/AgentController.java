package io.github.yuchencd.agentlab.web;

import io.github.yuchencd.agentlab.agent.AgentLoop;
import io.github.yuchencd.agentlab.agent.AgentService;
import io.github.yuchencd.agentlab.llm.Llm;
import io.github.yuchencd.agentlab.tool.ToolRegistry;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** HTTP surface: one blocking chat endpoint, one SSE stream, and a tool catalog. */
@RestController
@RequestMapping("/api/v1")
public class AgentController {

  private static final Logger log = LoggerFactory.getLogger(AgentController.class);
  private static final long SSE_TIMEOUT_MS = 180_000L;

  private final AgentService agentService;
  private final ToolRegistry toolRegistry;
  private final TaskExecutor taskExecutor;

  public AgentController(AgentService agentService, ToolRegistry toolRegistry, TaskExecutor taskExecutor) {
    this.agentService = agentService;
    this.toolRegistry = toolRegistry;
    this.taskExecutor = taskExecutor;
  }

  /** Blocking turn: returns the final reply plus every tool call that ran. */
  @PostMapping("/chat")
  public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
    return ChatResponse.from(
        agentService.chat(request.conversationId(), request.message(), AgentLoop.StepListener.NONE));
  }

  /**
   * Streaming turn. Events:
   * <ul>
   *   <li>{@code token} — a slice of answer text ({"text": "..."})</li>
   *   <li>{@code step} — one executed tool call</li>
   *   <li>{@code done} — the complete {@link ChatResponse}</li>
   *   <li>{@code error} — an {@code ApiError}</li>
   * </ul>
   */
  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    taskExecutor.execute(() -> {
      try {
        AgentService.ChatResult result = agentService.chat(
            request.conversationId(),
            request.message(),
            token -> sendSafely(emitter, "token", Map.of("text", token)),
            step -> sendSafely(emitter, "step", ChatResponse.ToolStep.from(step)));
        sendSafely(emitter, "done", ChatResponse.from(result));
        emitter.complete();
      } catch (RuntimeException exception) {
        log.warn("SSE 会话失败：{}", exception.getMessage());
        try {
          sendSafely(emitter, "error", ApiError.of(HttpStatus.BAD_GATEWAY, "agent_error", exception.getMessage()));
        } catch (RuntimeException ignored) {
          // the client already went away; nothing left to report to
        }
        emitter.completeWithError(exception);
      }
    });
    return emitter;
  }

  /** The tools currently advertised to the model. */
  @GetMapping("/tools")
  public List<Llm.ToolSpec> tools() {
    return toolRegistry.specs();
  }

  private void sendSafely(SseEmitter emitter, String event, Object payload) {
    try {
      emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
    } catch (IOException exception) {
      throw new UncheckedIOException("SSE 发送失败", exception);
    }
  }
}
