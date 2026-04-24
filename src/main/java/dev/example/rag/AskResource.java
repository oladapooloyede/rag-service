package dev.example.rag;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public class AskResource {

    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;
    private final int maxResults;

    public AskResource(EmbeddingStore<TextSegment> store,
                       EmbeddingModel embeddingModel,
                       @ConfigProperty(name = "rag.search.max-results", defaultValue = "3") int maxResults) {
        this.store = store;
        this.embeddingModel = embeddingModel;
        this.maxResults = maxResults;
    }

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public AskResponse ask(String question) {
        if (question == null || question.isBlank()) {
            throw new BadRequestException("Request body must contain a non-empty question");
        }

        Embedding queryEmbedding = embeddingModel.embed(question.strip()).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();

        List<Match> results = matches.stream()
                .map(m -> new Match(
                        m.score(),
                        m.embedded().text(),
                        m.embedded().metadata().toMap()))
                .toList();

        return new AskResponse(question, results);
    }

    public record AskResponse(String question, List<Match> matches) {}

    public record Match(double score, String text, java.util.Map<String, Object> metadata) {}
}
