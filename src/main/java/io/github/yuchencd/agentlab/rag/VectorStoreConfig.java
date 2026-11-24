package io.github.yuchencd.agentlab.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Chooses the vector store backend: in-memory by default, PostgreSQL/pgvector
 * when explicitly configured.
 */
@Configuration
public class VectorStoreConfig {

  @Bean
  @ConditionalOnProperty(name = "agent.rag.store", havingValue = "in-memory", matchIfMissing = true)
  VectorStore inMemoryVectorStore() {
    return new InMemoryVectorStore();
  }

  @Bean
  @ConditionalOnProperty(name = "agent.rag.store", havingValue = "pgvector")
  VectorStore pgVectorStore(PgVectorProperties properties) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        properties.url(), properties.username(), properties.password());
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    PgVectorStore store = new PgVectorStore(jdbcTemplate, properties.dimensions());
    store.ensureSchema();
    return store;
  }
}
