package io.github.yuchencd.agentlab.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosine-similarity vector store held in process memory.
 *
 * <p>Good enough for tens of thousands of chunks and a single instance; swap for
 * a persistent store (pgvector, Qdrant, …) when the corpus or deployment shape
 * demands it.</p>
 */
public class InMemoryVectorStore implements VectorStore {

  private final Map<String, Entry> entries = new ConcurrentHashMap<>();

  @Override
  public void add(Chunk chunk, float[] vector) {
    entries.put(chunk.id(), new Entry(chunk, vector.clone()));
  }

  @Override
  public List<ScoredChunk> search(float[] query, int topK) {
    if (topK <= 0 || entries.isEmpty()) {
      return List.of();
    }
    List<ScoredChunk> scored = new ArrayList<>(entries.size());
    for (Entry entry : entries.values()) {
      double score = cosine(query, entry.vector);
      if (score > 0) {
        scored.add(new ScoredChunk(entry.chunk, score));
      }
    }
    return scored.stream()
        .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
        .limit(topK)
        .toList();
  }

  @Override
  public int size() {
    return entries.size();
  }

  @Override
  public void clear() {
    entries.clear();
  }

  private static double cosine(float[] left, float[] right) {
    if (left.length != right.length) {
      return 0;
    }
    double dot = 0;
    double leftNorm = 0;
    double rightNorm = 0;
    for (int index = 0; index < left.length; index++) {
      dot += left[index] * right[index];
      leftNorm += left[index] * left[index];
      rightNorm += right[index] * right[index];
    }
    if (leftNorm == 0 || rightNorm == 0) {
      return 0;
    }
    return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
  }

  private record Entry(Chunk chunk, float[] vector) {
  }
}
