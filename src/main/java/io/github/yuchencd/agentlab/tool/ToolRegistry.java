package io.github.yuchencd.agentlab.tool;

import io.github.yuchencd.agentlab.llm.Llm;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The set of tools available to the agent, keyed by tool name. */
@Component
public class ToolRegistry {

  private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

  private final Map<String, Tool> tools = new LinkedHashMap<>();

  public ToolRegistry(List<Tool> discoveredTools) {
    discoveredTools.forEach(this::register);
    log.info("已注册 {} 个工具：{}", tools.size(), tools.keySet());
  }

  /** Registers one tool, refusing a duplicate name early instead of at call time. */
  public void register(Tool tool) {
    if (tools.putIfAbsent(tool.name(), tool) != null) {
      throw new IllegalStateException("工具重复注册：" + tool.name());
    }
  }

  public List<Tool> all() {
    return List.copyOf(tools.values());
  }

  /** Tool specs in the shape the LLM expects. */
  public List<Llm.ToolSpec> specs() {
    return all().stream()
        .map(tool -> new Llm.ToolSpec(tool.name(), tool.description(), tool.parameters()))
        .toList();
  }

  public Tool get(String name) {
    Tool tool = tools.get(name);
    if (tool == null) {
      throw new IllegalArgumentException("未知工具：" + name);
    }
    return tool;
  }

  public String execute(String name, String argumentsJson) {
    return get(name).execute(argumentsJson);
  }
}
