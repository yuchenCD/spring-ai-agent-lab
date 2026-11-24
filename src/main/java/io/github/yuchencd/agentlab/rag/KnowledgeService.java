package io.github.yuchencd.agentlab.rag;

import io.github.yuchencd.agentlab.config.AgentProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Ingests documents into the vector store and retrieves the most relevant
 * chunks for a query.
 *
 * <p>Deliberately independent of the HTTP layer and the agent: the REST endpoint
 * and the {@code knowledge_search} tool both call this service.</p>
 */
@Service
public class KnowledgeService {

  private final EmbeddingClient embeddingClient;
  private final TextChunker chunker;
  private final VectorStore vectorStore;
  private final int defaultTopK;

  public KnowledgeService(AgentProperties properties, EmbeddingClient embeddingClient, VectorStore vectorStore) {
    this.embeddingClient = embeddingClient;
    this.vectorStore = vectorStore;
    this.chunker = new TextChunker(properties.rag().chunkSize(), properties.rag().chunkOverlap());
    this.defaultTopK = Math.max(1, properties.rag().topK());
  }

  /** Splits, embeds and stores one document. */
  public IngestResult ingest(String source, String text) {
    String normalisedSource = source == null || source.isBlank() ? "inline" : source.strip();
    List<String> chunks = chunker.split(text);
    if (chunks.isEmpty()) {
      throw new IllegalArgumentException("text 不能为空");
    }
    List<float[]> vectors = embeddingClient.embedAll(chunks);
    for (int index = 0; index < chunks.size(); index++) {
      vectorStore.add(new Chunk(UUID.randomUUID().toString(), normalisedSource, chunks.get(index)), vectors.get(index));
    }
    return new IngestResult(normalisedSource, chunks.size());
  }

  /** Retrieves the top-K chunks most similar to the query. */
  public SearchResult search(String query, Integer topK) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("query 不能为空");
    }
    int limit = topK == null || topK <= 0 ? defaultTopK : Math.min(topK, 20);
    float[] vector = embeddingClient.embed(query);
    return new SearchResult(query, embeddingClient.providerName(), vectorStore.search(vector, limit));
  }

  public int size() {
    return vectorStore.size();
  }

  public void clear() {
    vectorStore.clear();
  }

  /** Result of ingesting one document. */
  public record IngestResult(String source, int chunks) {
  }

  /** Retrieval result, including which embedder produced the vectors. */
  public record SearchResult(String query, String embeddingProvider, List<ScoredChunk> results) {
  }
}
