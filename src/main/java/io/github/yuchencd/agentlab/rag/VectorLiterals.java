package io.github.yuchencd.agentlab.rag;

/** Formats float vectors for pgvector parameters. */
public final class VectorLiterals {

  private VectorLiterals() {
  }

  /** Renders a vector as the pgvector textual form: {@code [0.1,0.2,0.3]}. */
  public static String toLiteral(float[] vector) {
    StringBuilder builder = new StringBuilder(vector.length * 10 + 2);
    builder.append('[');
    for (int index = 0; index < vector.length; index++) {
      if (index > 0) {
        builder.append(',');
      }
      builder.append(vector[index]);
    }
    builder.append(']');
    return builder.toString();
  }
}
