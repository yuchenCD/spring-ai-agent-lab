package io.github.yuchencd.agentlab.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Model-facing retrieval tool: the agent calls it to look up local knowledge
 * instead of hallucinating.
 */
@Component
public class KnowledgeSearchTool implements io.github.yuchencd.agentlab.tool.Tool {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final KnowledgeService knowledgeService;

  public KnowledgeSearchTool(KnowledgeService knowledgeService) {
    this.knowledgeService = knowledgeService;
  }

  @Override
  public String name() {
    return "knowledge_search";
  }

  @Override
  public String description() {
    return "检索本地知识库，返回与查询最相关的文档片段（含来源与相似度）。回答涉及内部资料、项目约定、文档细节的问题前应调用。";
  }

  @Override
  public Map<String, Object> parameters() {
    return Map.of(
        "type", "object",
        "properties", Map.of(
            "query", Map.of("type", "string", "description", "检索用的自然语言问题或关键词"),
            "topK", Map.of("type", "integer", "description", "返回片段数，默认使用服务端配置")),
        "required", List.of("query"));
  }

  @Override
  public String execute(String argumentsJson) {
    JsonNode arguments;
    try {
      arguments = JSON.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
    } catch (Exception exception) {
      throw new IllegalArgumentException("arguments 不是合法 JSON：" + exception.getMessage());
    }
    String query = arguments.path("query").asText("");
    Integer topK = arguments.path("topK").isInt() ? arguments.path("topK").asInt() : null;

    KnowledgeService.SearchResult result = knowledgeService.search(query, topK);
    if (result.results().isEmpty()) {
      return "知识库中没有匹配内容（当前共 " + knowledgeService.size() + " 个片段）。";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("知识库检索结果（embedding=").append(result.embeddingProvider()).append("）：\n");
    for (int index = 0; index < result.results().size(); index++) {
      ScoredChunk scored = result.results().get(index);
      builder.append("\n[").append(index + 1).append("] source=").append(scored.chunk().source())
          .append(" score=").append(String.format("%.3f", scored.score())).append("\n")
          .append(scored.chunk().text()).append("\n");
    }
    return builder.toString();
  }
}
