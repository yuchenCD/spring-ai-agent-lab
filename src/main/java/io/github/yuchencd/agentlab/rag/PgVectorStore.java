package io.github.yuchencd.agentlab.rag;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistent vector store backed by PostgreSQL + pgvector.
 *
 * <p>Enabled with {@code agent.rag.store=pgvector}; the schema is created on
 * startup (`vector` extension, one table per dimensions configuration). Cosine
 * distance ({@code <=>}) is used, so retrieval semantics match the in-memory
 * implementation.</p>
 */
public class PgVectorStore implements VectorStore {

  private final JdbcTemplate jdbcTemplate;
  private final int dimensions;

  public PgVectorStore(JdbcTemplate jdbcTemplate, int dimensions) {
    if (dimensions <= 0) {
      throw new IllegalArgumentException("dimensions 必须为正数");
    }
    this.jdbcTemplate = jdbcTemplate;
    this.dimensions = dimensions;
  }

  /** Creates the pgvector extension and the chunk table if they do not exist. */
  public void ensureSchema() {
    jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS agentlab_chunks (
          id        text PRIMARY KEY,
          source    text NOT NULL,
          text      text NOT NULL,
          embedding vector(%d) NOT NULL
        )""".formatted(dimensions));
  }

  @Override
  public void add(Chunk chunk, float[] vector) {
    jdbcTemplate.update(
        "INSERT INTO agentlab_chunks (id, source, text, embedding) VALUES (?, ?, ?, ?::vector)",
        chunk.id(), chunk.source(), chunk.text(), VectorLiterals.toLiteral(vector));
  }

  @Override
  public List<ScoredChunk> search(float[] query, int topK) {
    if (topK <= 0) {
      return List.of();
    }
    String literal = VectorLiterals.toLiteral(query);
    return jdbcTemplate.query("""
            SELECT id, source, text, 1 - (embedding <=> ?::vector) AS score
            FROM agentlab_chunks
            WHERE embedding <=> ?::vector < 1
            ORDER BY embedding <=> ?::vector
            LIMIT ?""",
        (resultSet, rowNumber) -> new ScoredChunk(
            new Chunk(resultSet.getString("id"), resultSet.getString("source"), resultSet.getString("text")),
            resultSet.getDouble("score")),
        literal, literal, literal, topK);
  }

  @Override
  public int size() {
    Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM agentlab_chunks", Integer.class);
    return count == null ? 0 : count;
  }

  @Override
  public void clear() {
    jdbcTemplate.update("DELETE FROM agentlab_chunks");
  }
}
