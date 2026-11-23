package io.github.yuchencd.agentlab.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One chat request. Omit {@code conversationId} to start a new conversation. */
public record ChatRequest(
    @Size(max = 64, message = "conversationId 最长 64 字符") String conversationId,
    @NotBlank(message = "message 不能为空")
    @Size(max = 4000, message = "message 最长 4000 字符") String message) {
}
