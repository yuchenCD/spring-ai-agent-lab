package io.github.yuchencd.agentlab.web;

import io.github.yuchencd.agentlab.agent.AgentLoop;
import io.github.yuchencd.agentlab.agent.AgentService;
import java.util.List;

/** HTTP view of one completed agent turn. */
public record ChatResponse(
    String conversationId,
    String reply,
    List<ToolStep> steps,
    boolean stepLimitReached,
    String provider) {

  /** One executed tool call, exposed for observability and debugging. */
  public record ToolStep(int step, String tool, String arguments, String result) {

    public static ToolStep from(AgentLoop.Step step) {
      return new ToolStep(step.step(), step.tool(), step.arguments(), step.result());
    }
  }

  public static ChatResponse from(AgentService.ChatResult result) {
    return new ChatResponse(
        result.conversationId(),
        result.reply(),
        result.steps().stream().map(ToolStep::from).toList(),
        result.stepLimitReached(),
        result.provider());
  }
}
