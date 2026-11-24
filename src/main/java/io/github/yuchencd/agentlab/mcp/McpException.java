package io.github.yuchencd.agentlab.mcp;

/** Raised when an MCP server cannot be started, answers with an error, or times out. */
public class McpException extends RuntimeException {

  public McpException(String message) {
    super(message);
  }

  public McpException(String message, Throwable cause) {
    super(message, cause);
  }
}
