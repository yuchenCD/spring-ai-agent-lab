package io.github.yuchencd.agentlab.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

  @Test
  void returnsEmptyForBlankText() {
    assertThat(new TextChunker(100, 10).split("   ")).isEmpty();
    assertThat(new TextChunker(100, 10).split(null)).isEmpty();
  }

  @Test
  void keepsShortTextInOneChunk() {
    assertThat(new TextChunker(100, 10).split("  hello world  ")).containsExactly("hello world");
  }

  @Test
  void splitsLongTextWithOverlapAndBoundedSize() {
    String longText = "0123456789".repeat(120);
    List<String> chunks = new TextChunker(120, 20).split(longText);

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(120));
    // the tail of one chunk reappears at the start of the next one
    String first = chunks.get(0);
    String second = chunks.get(1);
    assertThat(first.substring(first.length() - 20)).isEqualTo(second.substring(0, 20));
  }

  @Test
  void prefersSentenceBoundaries() {
    String text = "第一句很重要。第二句也很重要。第三句同样重要。第四句结尾。";
    List<String> chunks = new TextChunker(20, 5).split(text);

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks.get(0)).endsWith("。");
  }

  @Test
  void rejectsInvalidConfiguration() {
    assertThatThrownBy(() -> new TextChunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}
