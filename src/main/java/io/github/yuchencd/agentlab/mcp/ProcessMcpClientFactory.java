package io.github.yuchencd.agentlab.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Spawns stdio MCP servers as child processes. */
@Component
public class ProcessMcpClientFactory implements McpClientFactory {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(20);

  private final ObjectMapper objectMapper;

  public ProcessMcpClientFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public McpClient connect(McpProperties.Server server) throws IOException {
    return new StdioMcpClient(
        server.command(), server.args(), server.env(), objectMapper, STARTUP_TIMEOUT);
  }
}
