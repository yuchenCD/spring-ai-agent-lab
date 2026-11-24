package io.github.yuchencd.agentlab.mcp;

import io.github.yuchencd.agentlab.tool.Tool;
import java.util.List;
import java.util.Map;

/**
 * Exposes one remote MCP tool as a local {@link Tool}.
 *
 * <p>The registered name is namespaced as {@code mcp__<server>__<tool>} so tool
 * names stay unique and their origin stays obvious in traces.</p>
 */
public class McpToolAdapter implements Tool {

  private final String serverName;
  private final McpClient client;
  private final McpClient.McpToolSpec spec;
  private final String toolName;

  public McpToolAdapter(String serverName, McpClient client, McpClient.McpToolSpec spec) {
    this.serverName = serverName;
    this.client = client;
    this.spec = spec;
    this.toolName = "mcp__" + slug(serverName) + "__" + slug(spec.name());
  }

  @Override
  public String name() {
    return toolName;
  }

  @Override
  public String description() {
    String remote = spec.description() == null || spec.description().isBlank()
        ? "MCP tool " + spec.name() + " from server " + serverName
        : spec.description();
    return "[MCP:" + serverName + "] " + remote;
  }

  @Override
  public Map<String, Object> parameters() {
    Map<String, Object> schema = spec.inputSchema();
    if (schema == null || schema.isEmpty()) {
      return Map.of("type", "object");
    }
    return schema;
  }

  @Override
  public String execute(String argumentsJson) {
    return client.callTool(spec.name(), argumentsJson);
  }

  private static String slug(String value) {
    return value == null ? "" : value.replaceAll("[^A-Za-z0-9_]", "_");
  }

  /** Remote tool names this adapter wraps, for diagnostics. */
  public List<String> remoteNames() {
    return List.of(serverName, spec.name());
  }
}
