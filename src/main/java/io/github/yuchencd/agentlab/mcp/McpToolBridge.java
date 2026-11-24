package io.github.yuchencd.agentlab.mcp;

import io.github.yuchencd.agentlab.tool.ToolRegistry;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Connects the configured MCP servers once the application is ready and
 * registers their tools into the shared {@link ToolRegistry}.
 *
 * <p>Starting after {@code ApplicationReadyEvent} keeps the HTTP server usable
 * even when an MCP server is slow or broken: the failure is logged and that
 * server's tools are simply absent.</p>
 */
@Component
public class McpToolBridge {

  private static final Logger log = LoggerFactory.getLogger(McpToolBridge.class);

  private final McpProperties properties;
  private final ToolRegistry toolRegistry;
  private final McpClientFactory clientFactory;
  private final List<McpClient> clients = new CopyOnWriteArrayList<>();

  public McpToolBridge(McpProperties properties, ToolRegistry toolRegistry, McpClientFactory clientFactory) {
    this.properties = properties;
    this.toolRegistry = toolRegistry;
    this.clientFactory = clientFactory;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void registerServerTools() {
    for (McpProperties.Server server : properties.servers()) {
      if (!server.enabled()) {
        log.info("MCP server {} 已禁用，跳过", server.name());
        continue;
      }
      McpClient client = null;
      try {
        client = clientFactory.connect(server);
        String protocolVersion = client.initialize();
        List<McpClient.McpToolSpec> tools = client.listTools();
        int registered = 0;
        for (McpClient.McpToolSpec spec : tools) {
          McpToolAdapter adapter = new McpToolAdapter(server.name(), client, spec);
          try {
            toolRegistry.register(adapter);
            registered++;
          } catch (IllegalStateException duplicate) {
            log.warn("MCP 工具 {} 与已有工具重名，跳过", adapter.name());
          }
        }
        clients.add(client);
        log.info("MCP server {} 已接入（protocol={}，注册 {} 个工具）", server.name(), protocolVersion, registered);
      } catch (RuntimeException | java.io.IOException exception) {
        log.warn("MCP server {} 接入失败，跳过：{}", server.name(), exception.getMessage());
        if (client != null) {
          client.close();
        }
      }
    }
  }

  @PreDestroy
  public void closeClients() {
    for (McpClient client : clients) {
      try {
        client.close();
      } catch (RuntimeException exception) {
        log.warn("关闭 MCP 客户端失败：{}", exception.getMessage());
      }
    }
    clients.clear();
  }
}
