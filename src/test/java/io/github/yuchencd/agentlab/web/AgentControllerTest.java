package io.github.yuchencd.agentlab.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yuchencd.agentlab.agent.AgentService;
import io.github.yuchencd.agentlab.llm.Llm;
import io.github.yuchencd.agentlab.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AgentService agentService;

  @MockitoBean
  private ToolRegistry toolRegistry;

  @MockitoBean
  private TaskExecutor taskExecutor;

  @Test
  void returnsReplyForBlockingChat() throws Exception {
    when(agentService.chat(eq("c-1"), eq("你好"), any()))
        .thenReturn(new AgentService.ChatResult("c-1", "你好呀", List.of(), false, "mock"));

    mockMvc.perform(post("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"conversationId\":\"c-1\",\"message\":\"你好\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conversationId").value("c-1"))
        .andExpect(jsonPath("$.reply").value("你好呀"))
        .andExpect(jsonPath("$.provider").value("mock"));
  }

  @Test
  void rejectsBlankMessage() throws Exception {
    mockMvc.perform(post("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"));
  }

  @Test
  void listsAdvertisedTools() throws Exception {
    when(toolRegistry.specs())
        .thenReturn(List.of(new Llm.ToolSpec("calculator", "计算", Map.of("type", "object"))));

    mockMvc.perform(get("/api/v1/tools"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("calculator"));
  }
}
