package io.github.yuchencd.agentlab;

import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.mcp.McpProperties;
import io.github.yuchencd.agentlab.rag.PgVectorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Data source auto-configuration is excluded on purpose: the default profile
 * needs no database, and the optional pgvector store builds its own
 * {@code DriverManagerDataSource} only when {@code agent.rag.store=pgvector}.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties({AgentProperties.class, McpProperties.class, PgVectorProperties.class})
public class AgentLabApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentLabApplication.class, args);
  }
}
