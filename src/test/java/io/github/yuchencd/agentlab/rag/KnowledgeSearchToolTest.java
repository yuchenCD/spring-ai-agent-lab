package io.github.yuchencd.agentlab.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuchencd.agentlab.config.AgentProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KnowledgeSearchToolTest {

  private final KnowledgeService service = service();
  private final KnowledgeSearchTool tool = new KnowledgeSearchTool(service);

  @Test
  void returnsFormattedHitsForMatchingQuery() {
    service.ingest("runbook", "部署流程：使用 GitHub Actions 构建镜像并推送到镜像仓库。");

    String result = tool.execute("{\"query\":\"部署流程\",\"topK\":1}");

    assertThat(result).contains("source=runbook").contains("score=").contains("GitHub Actions");
    assertThat(tool.name()).isEqualTo("knowledge_search");
    assertThat(tool.parameters()).containsKey("properties");
  }

  @Test
  void reportsEmptyKnowledgeBase() {
    assertThat(tool.execute("{\"query\":\"不存在的内容\"}")).contains("没有匹配内容");
  }

  @Test
  void rejectsInvalidArguments() {
    assertThatThrownBy(() -> tool.execute("not-json")).isInstanceOf(IllegalArgumentException.class);
  }

  private static KnowledgeService service() {
    AgentProperties properties = new AgentProperties(
        null,
        null,
        new AgentProperties.Memory(10, "in-memory", Duration.ofHours(1)),
        new AgentProperties.Embedding("", "", "", 256),
        new AgentProperties.Rag(200, 40, 2));
    return new KnowledgeService(properties, new HashingEmbeddingClient(256), new InMemoryVectorStore());
  }
}
