package io.github.yuchencd.agentlab.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits documents into overlapping chunks, preferring paragraph and sentence
 * boundaries so retrieved snippets stay readable.
 *
 * <p>Sizes are measured in characters: simple, deterministic, and good enough
 * for Chinese text where word tokenisation is not free.</p>
 */
public class TextChunker {

  private final int chunkSize;
  private final int overlap;

  public TextChunker(int chunkSize, int overlap) {
    if (chunkSize <= 0) {
      throw new IllegalArgumentException("chunkSize 必须为正数");
    }
    this.chunkSize = chunkSize;
    this.overlap = Math.max(0, Math.min(overlap, chunkSize - 1));
  }

  public List<String> split(String text) {
    String normalised = text == null ? "" : text.strip();
    if (normalised.isEmpty()) {
      return List.of();
    }
    if (normalised.length() <= chunkSize) {
      return List.of(normalised);
    }

    List<String> chunks = new ArrayList<>();
    int start = 0;
    while (start < normalised.length()) {
      int tentativeEnd = Math.min(start + chunkSize, normalised.length());
      int end = tentativeEnd;
      if (tentativeEnd < normalised.length()) {
        int boundary = lastBoundary(normalised, start, tentativeEnd);
        if (boundary > start + chunkSize / 2) {
          end = boundary;
        }
      }
      String chunk = normalised.substring(start, end).strip();
      if (!chunk.isEmpty()) {
        chunks.add(chunk);
      }
      if (end >= normalised.length()) {
        break;
      }
      start = Math.max(end - overlap, start + 1);
    }
    return List.copyOf(chunks);
  }

  private static int lastBoundary(String text, int from, int to) {
    for (int index = to; index > from; index--) {
      char current = text.charAt(index - 1);
      if (current == '\n') {
        return index;
      }
      if (current == '。' || current == '！' || current == '？' || current == '；'
          || current == '.' || current == '!' || current == '?' || current == ';') {
        return index;
      }
      if (current == ' ') {
        return index;
      }
    }
    return to;
  }
}
