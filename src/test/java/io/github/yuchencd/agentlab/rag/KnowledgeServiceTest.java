package io.github.yuchencd.agentlab.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yuchencd.agentlab.config.AgentProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class KnowledgeServiceTest {

  private final KnowledgeService service = service();

  @Test
  void ingestsAndRetrievesRelevantChunk() {
    KnowledgeService.IngestResult ingested = service.ingest(
        "runbook",
        "部署流程：使用 GitHub Actions 构建镜像并推送到镜像仓库。\n\n"
            + "监控：使用 Prometheus 采集指标，Grafana 展示看板。");

    assertThat(ingested.chunks()).isGreaterThanOrEqualTo(1);
    assertThat(service.size()).isGreaterThanOrEqualTo(1);

    KnowledgeService.SearchResult result = service.search("监控指标", 2);

    assertThat(result.embeddingProvider()).contains("hashing");
    assertThat(result.results()).isNotEmpty();
    assertThat(result.results().get(0).chunk().source()).isEqualTo("runbook");
    assertThat(result.results().get(0).chunk().text()).contains("监控");
  }

  @Test
  void defaultsSourceAndRejectsEmptyText() {
    service.ingest(null, "一段没有来源的文档内容，用于测试默认 source。");
    assertThat(service.search("文档", 1).results().get(0).chunk().source()).isEqualTo("inline");

    assertThatThrownBy(() -> service.ingest("x", "   ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankQueryAndSupportsClear() {
    service.ingest("s", "内容");
    assertThatThrownBy(() -> service.search("  ", 2)).isInstanceOf(IllegalArgumentException.class);

    service.clear();
    assertThat(service.size()).isZero();
  }

  private static KnowledgeService service() {
    AgentProperties properties = new AgentProperties(
        null,
        null,
        new AgentProperties.Memory(10, "in-memory", Duration.ofHours(1)),
        new AgentProperties.Embedding("", "", "", 256),
        new AgentProperties.Rag(80, 20, 2));
    return new KnowledgeService(properties, new HashingEmbeddingClient(256), new InMemoryVectorStore());
  }
}
