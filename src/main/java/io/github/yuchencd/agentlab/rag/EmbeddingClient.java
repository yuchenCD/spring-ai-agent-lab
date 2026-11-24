package io.github.yuchencd.agentlab.rag;

import java.util.List;

/** Turns text into vectors for retrieval. */
public interface EmbeddingClient {

  /** Embeds one text. */
  float[] embed(String text);

  /** Embeds a batch of texts, preserving order. */
  List<float[]> embedAll(List<String> texts);

  /** Vector size this client produces. */
  int dimensions();

  /** Provider label shown in logs and diagnostics. */
  String providerName();
}
