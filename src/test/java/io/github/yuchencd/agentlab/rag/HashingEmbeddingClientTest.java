package io.github.yuchencd.agentlab.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class HashingEmbeddingClientTest {

  private final HashingEmbeddingClient client = new HashingEmbeddingClient(128);

  @Test
  void isDeterministic() {
    assertThat(client.embed("hello agent")).containsExactly(client.embed("hello agent"));
  }

  @Test
  void producesNormalisedVectors() {
    float[] vector = client.embed("spring boot ai agent");

    double norm = 0;
    for (float value : vector) {
      norm += value * value;
    }
    assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    assertThat(vector).hasSize(128);
  }

  @Test
  void similarTextScoresHigherThanUnrelatedText() {
    float[] base = client.embed("部署流程 使用 GitHub Actions 构建镜像");
    float[] similar = client.embed("部署流程：GitHub Actions 构建");
    float[] unrelated = client.embed("今天天气不错适合散步");

    assertThat(cosine(base, similar)).isGreaterThan(cosine(base, unrelated));
  }

  @Test
  void embedsBatchesInOrder() {
    List<float[]> vectors = client.embedAll(List.of("one", "two"));

    assertThat(vectors).hasSize(2);
    assertThat(vectors.get(0)).containsExactly(client.embed("one"));
    assertThat(vectors.get(1)).containsExactly(client.embed("two"));
  }

  @Test
  void reportsProviderAndDimensions() {
    assertThat(client.dimensions()).isEqualTo(128);
    assertThat(client.providerName()).contains("hashing");
  }

  @Test
  void rejectsInvalidDimensions() {
    assertThatThrownBy(() -> new HashingEmbeddingClient(0)).isInstanceOf(IllegalArgumentException.class);
  }

  private static double cosine(float[] left, float[] right) {
    double dot = 0;
    for (int index = 0; index < left.length; index++) {
      dot += left[index] * right[index];
    }
    return dot;
  }
}
