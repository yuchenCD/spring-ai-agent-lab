package io.github.yuchencd.agentlab.memory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yuchencd.agentlab.config.AgentProperties;
import io.github.yuchencd.agentlab.llm.Llm;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryChatMemoryTest {

  private static final AgentProperties PROPERTIES = new AgentProperties(
      null,
      null,
      new AgentProperties.Memory(4, "in-memory", Duration.ofHours(24)),
      null,
      null);

  private final InMemoryChatMemory memory = new InMemoryChatMemory(PROPERTIES);

  @Test
  void storesAndReturnsMessagesInOrder() {
    memory.append("c1", Llm.Message.user("第一句"), Llm.Message.assistant("回复一"));

    assertThat(memory.get("c1")).extracting(Llm.Message::content).containsExactly("第一句", "回复一");
    assertThat(memory.get("other")).isEmpty();
  }

  @Test
  void trimsOldestMessagesBeyondBound() {
    for (int index = 0; index < 6; index++) {
      memory.append("c1", Llm.Message.user("m" + index));
    }

    assertThat(memory.get("c1")).hasSize(4);
    assertThat(memory.get("c1")).extracting(Llm.Message::content).containsExactly("m2", "m3", "m4", "m5");
  }

  @Test
  void clearsOneConversation() {
    memory.append("c1", Llm.Message.user("hello"));
    memory.append("c2", Llm.Message.user("keep"));

    memory.clear("c1");

    assertThat(memory.get("c1")).isEmpty();
    assertThat(memory.get("c2")).hasSize(1);
  }
}
