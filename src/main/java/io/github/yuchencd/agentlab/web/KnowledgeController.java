package io.github.yuchencd.agentlab.web;

import io.github.yuchencd.agentlab.rag.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ingest and search endpoints for the in-process knowledge base. */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

  private final KnowledgeService knowledgeService;

  public KnowledgeController(KnowledgeService knowledgeService) {
    this.knowledgeService = knowledgeService;
  }

  @PostMapping
  public KnowledgeIngestResponse ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
    KnowledgeService.IngestResult result = knowledgeService.ingest(request.source(), request.text());
    return new KnowledgeIngestResponse(result.source(), result.chunks(), knowledgeService.size());
  }

  @GetMapping("/search")
  public KnowledgeSearchResponse search(
      @RequestParam("q") String query,
      @RequestParam(name = "k", defaultValue = "4") int topK) {
    KnowledgeService.SearchResult result = knowledgeService.search(query, topK);
    return new KnowledgeSearchResponse(
        result.query(),
        result.embeddingProvider(),
        result.results().stream()
            .map(scored -> new KnowledgeSearchResponse.Hit(
                scored.chunk().source(), scored.chunk().text(), Math.round(scored.score() * 1000) / 1000.0))
            .toList());
  }
}
