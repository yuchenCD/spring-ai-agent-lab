package io.github.yuchencd.agentlab.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryVectorStoreTest {

  private final InMemoryVectorStore store = new InMemoryVectorStore();

  @Test
  void ranksByCosineSimilarity() {
    store.add(new Chunk("a", "runbook", "部署流程"), new float[] {1f, 0f});
    store.add(new Chunk("b", "runbook", "监控指标"), new float[] {0.6f, 0.8f});

    List<ScoredChunk> hits = store.search(new float[] {1f, 0f}, 2);

    assertThat(hits).hasSize(2);
    assertThat(hits.get(0).chunk().id()).isEqualTo("a");
    assertThat(hits.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(hits.get(1).score()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-6));
  }

  @Test
  void honoursTopKAndSkipsZeroScores() {
    store.add(new Chunk("a", "s", "one"), new float[] {1f, 0f});
    store.add(new Chunk("b", "s", "two"), new float[] {0.5f, 0.5f});

    assertThat(store.search(new float[] {1f, 0f}, 1)).hasSize(1);
    assertThat(store.search(new float[] {0f, 1f}, 5)).extracting(hit -> hit.chunk().id()).containsExactly("b");
  }

  @Test
  void ignoresDimensionMismatchAndSupportsLifecycle() {
    store.add(new Chunk("a", "s", "one"), new float[] {1f, 0f});
    store.add(new Chunk("b", "s", "other dims"), new float[] {1f, 0f, 0f});

    assertThat(store.size()).isEqualTo(2);
    assertThat(store.search(new float[] {1f, 0f}, 5)).hasSize(1);

    store.clear();
    assertThat(store.size()).isZero();
    assertThat(store.search(new float[] {1f, 0f}, 5)).isEmpty();
  }
}
