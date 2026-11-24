package io.github.yuchencd.agentlab.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PostgreSQL + pgvector connection, used only when {@code agent.rag.store=pgvector}.
 *
 * <p>The vector dimension must match the embedding provider: the offline hashing
 * default is {@code agent.embedding.dimensions} (256), while real providers use
 * their own dimension (e.g. 1536 for text-embedding-3-small).</p>
 */
@ConfigurationProperties(prefix = "agent.pgvector")
public record PgVectorProperties(String url, String username, String password, int dimensions) {

  public PgVectorProperties {
    dimensions = dimensions > 0 ? dimensions : 256;
  }
}
