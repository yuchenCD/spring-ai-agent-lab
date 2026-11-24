package io.github.yuchencd.agentlab.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One document to ingest into the knowledge base. */
public record KnowledgeIngestRequest(
    @Size(max = 100, message = "source 最长 100 字符") String source,
    @NotBlank(message = "text 不能为空")
    @Size(max = 20000, message = "text 最长 20000 字符") String text) {
}
