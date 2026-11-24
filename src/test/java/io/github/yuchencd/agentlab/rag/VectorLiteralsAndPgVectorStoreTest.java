package io.github.yuchencd.agentlab.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class VectorLiteralsAndPgVectorStoreTest {

  @Test
  void formatsVectorsForPgVector() {
    assertThat(VectorLiterals.toLiteral(new float[] {1f, 0.5f, -0.25f})).isEqualTo("[1.0,0.5,-0.25]");
    assertThat(VectorLiterals.toLiteral(new float[] {})).isEqualTo("[]");
  }

  @Test
  void writesChunksWithVectorLiteral() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    PgVectorStore store = new PgVectorStore(jdbcTemplate, 2);

    store.add(new Chunk("id-1", "runbook", "部署流程"), new float[] {1f, 0f});

    verify(jdbcTemplate).update(
        "INSERT INTO agentlab_chunks (id, source, text, embedding) VALUES (?, ?, ?, ?::vector)",
        "id-1", "runbook", "部署流程", "[1.0,0.0]");
  }

  @Test
  void reportsSizeAndClears() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForObject("SELECT count(*) FROM agentlab_chunks", Integer.class)).thenReturn(7);
    PgVectorStore store = new PgVectorStore(jdbcTemplate, 2);

    assertThat(store.size()).isEqualTo(7);
    assertThat(store.search(new float[] {1f, 0f}, 0)).isEmpty();

    store.clear();
    verify(jdbcTemplate).update("DELETE FROM agentlab_chunks");
    verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).queryForObject(anyString(), eq(Integer.class));
  }
}
