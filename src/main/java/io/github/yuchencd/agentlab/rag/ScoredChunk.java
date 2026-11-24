package io.github.yuchencd.agentlab.rag;

/** A retrieval hit: the stored chunk plus its similarity score. */
public record ScoredChunk(Chunk chunk, double score) {
}
