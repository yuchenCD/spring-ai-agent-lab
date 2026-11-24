package io.github.yuchencd.agentlab.mcp;

import java.util.List;
import java.util.Map;

/** Minimal MCP client seam: handshake, tool discovery, tool invocation. */
public interface McpClient extends AutoCloseable {

  /**
   * Performs the MCP initialize handshake.
   *
   * @return the protocol version the server agreed on
   */
  String initialize();

  /** Lists the tools the server exposes. */
  List<McpToolSpec> listTools();

  /**
   * Calls one remote tool.
   *
   * @param name           remote tool name
   * @param argumentsJson  JSON object string with the tool arguments
   * @return concatenated text content of the tool result
   */
  String callTool(String name, String argumentsJson);

  @Override
  void close();

  /** A tool advertised by an MCP server. */
  record McpToolSpec(String name, String description, Map<String, Object> inputSchema) {
  }
}
