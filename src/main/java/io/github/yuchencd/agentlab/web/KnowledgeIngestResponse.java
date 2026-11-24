package io.github.yuchencd.agentlab.web;

/** Result of ingesting one document. */
public record KnowledgeIngestResponse(String source, int chunks, int totalChunks) {
}
