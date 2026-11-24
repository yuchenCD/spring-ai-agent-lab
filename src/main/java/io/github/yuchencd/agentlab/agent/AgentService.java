package io.github.yuchencd.agentlab.agent;

import io.github.yuchencd.agentlab.llm.Llm;
import io.github.yuchencd.agentlab.llm.LlmClient;
import io.github.yuchencd.agentlab.memory.ChatMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one chat turn: load history, prepend the system prompt, run the
 * loop, then persist the turn. Business HTTP layers only see {@link ChatResult}.
 */
@Service
public class AgentService {

  /** Guardrails for the model: concise, tool-aware, and honest about failures. */
  static final String SYSTEM_PROMPT = """
      你是一名严谨的 Java 后端工程师 Agent。
      - 回答简洁、可执行：先给结论，再给必要的细节。
      - 需要精确计算时调用 calculator 工具；需要当前时间时调用 clock 工具。
      - 工具返回以 ERROR 开头表示调用失败：如实说明失败原因，不要编造结果。
      - 不确定时明确说"不确定"，不要虚构事实。
      """;

  private final LlmClient llmClient;
  private final ChatMemory memory;
  private final AgentLoop agentLoop;

  public AgentService(LlmClient llmClient, ChatMemory memory, AgentLoop agentLoop) {
    this.llmClient = llmClient;
    this.memory = memory;
    this.agentLoop = agentLoop;
  }

  /** Runs a turn without streaming answer tokens. */
  public ChatResult chat(String conversationId, String userMessage, AgentLoop.StepListener listener) {
    return chat(conversationId, userMessage, token -> {
    }, listener);
  }

  /**
   * Runs a turn, forwarding streamed answer text through {@code onToken}.
   *
   * @param conversationId existing conversation id, or {@code null}/blank for a new one
   * @param userMessage    the user's message
   * @param onToken        receives streamed answer text as it is generated
   * @param listener       receives one event per executed tool call
   * @return the completed turn
   */
  public ChatResult chat(
      String conversationId,
      String userMessage,
      Consumer<String> onToken,
      AgentLoop.StepListener listener) {
    String id = conversationId == null || conversationId.isBlank()
        ? UUID.randomUUID().toString()
        : conversationId;

    List<Llm.Message> conversation = new ArrayList<>();
    conversation.add(Llm.Message.system(SYSTEM_PROMPT));
    conversation.addAll(memory.get(id));
    conversation.add(Llm.Message.user(userMessage));

    AgentLoop.AgentRun run = agentLoop.run(conversation, onToken, listener);
    memory.append(id, Llm.Message.user(userMessage), Llm.Message.assistant(run.reply()));

    return new ChatResult(id, run.reply(), run.steps(), run.stepLimitReached(), llmClient.providerName());
  }

  /** One completed conversation turn. */
  public record ChatResult(
      String conversationId,
      String reply,
      List<AgentLoop.Step> steps,
      boolean stepLimitReached,
      String provider) {
  }
}
