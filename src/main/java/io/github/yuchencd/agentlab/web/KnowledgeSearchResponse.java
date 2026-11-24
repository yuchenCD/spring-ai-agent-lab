package io.github.yuchencd.agentlab.web;

import java.util.List;

/** Retrieval result exposed over HTTP for debugging and manual use. */
public record KnowledgeSearchResponse(String query, String embeddingProvider, List<Hit> hits) {

  public record Hit(String source, String text, double score) {
  }
}
