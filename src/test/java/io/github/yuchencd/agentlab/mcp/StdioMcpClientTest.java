package io.github.yuchencd.agentlab.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StdioMcpClientTest {

  private static Path script;

  @BeforeAll
  static void prepare() {
    URL location = Objects.requireNonNull(
        StdioMcpClientTest.class.getResource("/fake-mcp-server.py"), "fake MCP server script is missing");
    try {
      script = Path.of(location.toURI());
    } catch (Exception exception) {
      throw new IllegalStateException("cannot resolve fake MCP server script", exception);
    }
    Assumptions.assumeTrue(pythonAvailable(), "python3 is not available; skipping stdio MCP protocol test");
  }

  @Test
  void initializesDiscoversAndCallsTools() throws IOException {
    McpClient client = new StdioMcpClient(
        "python3", List.of(script.toString()), Map.of(), new ObjectMapper(), Duration.ofSeconds(15));
    try (client) {
      assertThat(client.initialize()).isEqualTo("2024-11-05");

      List<McpClient.McpToolSpec> tools = client.listTools();
      assertThat(tools).extracting(McpClient.McpToolSpec::name).containsExactly("echo");
      assertThat(tools.get(0).inputSchema()).containsKey("properties");

      assertThat(client.callTool("echo", "{\"text\":\"hello\"}")).isEqualTo("echo: hello");
      assertThatThrownBy(() -> client.callTool("missing-tool", "{}"))
          .isInstanceOf(McpException.class)
          .hasMessageContaining("Unknown tool");
    }
  }

  private static boolean pythonAvailable() {
    try {
      Process process = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      int exitCode = finished ? process.exitValue() : -1;
      process.destroyForcibly();
      return finished && exitCode == 0;
    } catch (Exception exception) {
      return false;
    }
  }
}
