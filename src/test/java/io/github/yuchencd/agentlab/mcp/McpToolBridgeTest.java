package io.github.yuchencd.agentlab.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuchencd.agentlab.tool.ToolRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolBridgeTest {

  @Test
  void registersRemoteToolsIntoTheRegistry() {
    ToolRegistry registry = new ToolRegistry(List.of());
    McpToolBridge bridge = new McpToolBridge(properties(true), registry, server -> new FakeMcpClient());

    bridge.registerServerTools();

    assertThat(registry.specs()).extracting(spec -> spec.name()).containsExactly("mcp__fake__echo");
    assertThat(registry.execute("mcp__fake__echo", "{\"text\":\"hi\"}")).isEqualTo("echo: hi");
    bridge.closeClients();
  }

  @Test
  void skipsDisabledServers() {
    ToolRegistry registry = new ToolRegistry(List.of());
    McpToolBridge bridge = new McpToolBridge(properties(false), registry, server -> new FakeMcpClient());

    bridge.registerServerTools();

    assertThat(registry.specs()).isEmpty();
  }

  @Test
  void keepsRunningWhenAServerFailsToStart() {
    ToolRegistry registry = new ToolRegistry(List.of());
    McpToolBridge bridge = new McpToolBridge(properties(true), registry, server -> {
      throw new IOException("cannot start");
    });

    bridge.registerServerTools();

    assertThat(registry.specs()).isEmpty();
  }

  private static McpProperties properties(boolean enabled) {
    return new McpProperties(List.of(
        new McpProperties.Server("fake", "python3", List.of("fake-mcp-server.py"), Map.of(), enabled)));
  }

  private static final class FakeMcpClient implements McpClient {

    @Override
    public String initialize() {
      return "2024-11-05";
    }

    @Override
    public List<McpToolSpec> listTools() {
      return List.of(new McpToolSpec("echo", "Echo the given text", Map.of("type", "object")));
    }

    @Override
    public String callTool(String name, String argumentsJson) {
      return "echo: hi";
    }

    @Override
    public void close() {
    }
  }
}
