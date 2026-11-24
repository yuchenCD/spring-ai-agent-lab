package io.github.yuchencd.agentlab.mcp;

import java.io.IOException;

/** Creates MCP clients; replaceable so the bridge can be tested without child processes. */
@FunctionalInterface
public interface McpClientFactory {

  McpClient connect(McpProperties.Server server) throws IOException;
}
