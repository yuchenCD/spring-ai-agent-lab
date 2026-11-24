package io.github.yuchencd.agentlab.rag;

import java.util.List;

/**
 * Vector storage seam.
 *
 * <p>The default bean is an in-memory implementation; a pgvector-backed store
 * can be added later without touching the knowledge service or the agent tool.</p>
 */
public interface VectorStore {

  void add(Chunk chunk, float[] vector);

  List<ScoredChunk> search(float[] query, int topK);

  int size();

  void clear();
}
