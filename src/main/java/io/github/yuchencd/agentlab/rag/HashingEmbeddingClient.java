package io.github.yuchencd.agentlab.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline, deterministic embedder for demos and tests: character bigrams hashed
 * into a fixed number of buckets, then L2-normalised.
 *
 * <p>This is <strong>not</strong> a semantic model — it captures lexical overlap
 * only. It exists so retrieval can run end to end without an API key; set
 * {@code agent.embedding.api-key} to use a real provider instead.</p>
 */
public class HashingEmbeddingClient implements EmbeddingClient {

  private final int dimensions;

  public HashingEmbeddingClient(int dimensions) {
    if (dimensions <= 0) {
      throw new IllegalArgumentException("dimensions 必须为正数");
    }
    this.dimensions = dimensions;
  }

  @Override
  public float[] embed(String text) {
    float[] vector = new float[dimensions];
    String normalised = normalise(text);
    if (normalised.length() < 2) {
      vector[Math.floorMod(normalised.hashCode(), dimensions)] = 1f;
      return vector;
    }
    for (int index = 0; index + 1 < normalised.length(); index++) {
      int hash = (normalised.charAt(index) * 31 + normalised.charAt(index + 1)) * 0x9E3779B1;
      vector[Math.floorMod(hash, dimensions)] += 1f;
    }
    l2Normalise(vector);
    return vector;
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    List<float[]> vectors = new ArrayList<>(texts.size());
    for (String text : texts) {
      vectors.add(embed(text));
    }
    return vectors;
  }

  @Override
  public int dimensions() {
    return dimensions;
  }

  @Override
  public String providerName() {
    return "hashing-offline";
  }

  private static String normalise(String text) {
    return text == null ? "" : text.toLowerCase().replaceAll("\\s+", " ").strip();
  }

  private static void l2Normalise(float[] vector) {
    double sum = 0;
    for (float value : vector) {
      sum += value * value;
    }
    double norm = Math.sqrt(sum);
    if (norm == 0) {
      return;
    }
    for (int index = 0; index < vector.length; index++) {
      vector[index] = (float) (vector[index] / norm);
    }
  }
}
