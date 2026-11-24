package io.github.yuchencd.agentlab.rag;

/** One stored piece of a document. */
public record Chunk(String id, String source, String text) {
}
