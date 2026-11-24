package io.github.yuchencd.agentlab.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yuchencd.agentlab.rag.Chunk;
import io.github.yuchencd.agentlab.rag.KnowledgeService;
import io.github.yuchencd.agentlab.rag.ScoredChunk;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KnowledgeController.class)
class KnowledgeControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private KnowledgeService knowledgeService;

  @Test
  void ingestsDocument() throws Exception {
    when(knowledgeService.ingest("runbook", "部署流程")).thenReturn(new KnowledgeService.IngestResult("runbook", 2));
    when(knowledgeService.size()).thenReturn(5);

    mockMvc.perform(post("/api/v1/knowledge")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"runbook\",\"text\":\"部署流程\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("runbook"))
        .andExpect(jsonPath("$.chunks").value(2))
        .andExpect(jsonPath("$.totalChunks").value(5));
  }

  @Test
  void rejectsBlankText() throws Exception {
    mockMvc.perform(post("/api/v1/knowledge")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"source\":\"runbook\",\"text\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"));
  }

  @Test
  void searchesKnowledgeBase() throws Exception {
    when(knowledgeService.search("部署", 4)).thenReturn(new KnowledgeService.SearchResult(
        "部署", "hashing-offline",
        List.of(new ScoredChunk(new Chunk("id-1", "runbook", "部署流程内容"), 0.912))));

    mockMvc.perform(get("/api/v1/knowledge/search").param("q", "部署"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.embeddingProvider").value("hashing-offline"))
        .andExpect(jsonPath("$.hits[0].source").value("runbook"))
        .andExpect(jsonPath("$.hits[0].text").value("部署流程内容"))
        .andExpect(jsonPath("$.hits[0].score").value(0.912));
  }
}
