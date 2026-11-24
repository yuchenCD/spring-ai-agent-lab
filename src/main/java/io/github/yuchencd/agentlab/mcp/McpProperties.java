package io.github.yuchencd.agentlab.mcp;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP servers to bridge into the tool registry.
 *
 * <p>Each server is spawned as a child process speaking newline-delimited
 * JSON-RPC over stdio (the MCP stdio transport). An empty list disables the
 * bridge entirely.</p>
 */
@ConfigurationProperties(prefix = "agent.mcp")
public record McpProperties(List<Server> servers) {

  public McpProperties {
    servers = servers == null ? List.of() : List.copyOf(servers);
  }

  /**
   * One MCP server process.
   *
   * @param name    prefix for registered tool names ({@code mcp__<name>__<tool>})
   * @param command executable to spawn
   * @param args    arguments passed to the executable
   * @param env     extra environment variables for the child process
   * @param enabled whether the bridge should start this server
   */
  public record Server(
      String name,
      String command,
      List<String> args,
      Map<String, String> env,
      boolean enabled) {

    public Server {
      args = args == null ? List.of() : List.copyOf(args);
      env = env == null ? Map.of() : Map.copyOf(env);
    }
  }
}
