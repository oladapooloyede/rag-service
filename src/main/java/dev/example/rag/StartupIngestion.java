package dev.example.rag;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class StartupIngestion {

    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel embeddingModel;
    private final String sourceUrl;
    private final int paragraphsPerChunk;
    private final boolean enabled;

    public StartupIngestion(EmbeddingStore<TextSegment> store,
                            EmbeddingModel embeddingModel,
                            @ConfigProperty(name = "rag.source.url") String sourceUrl,
                            @ConfigProperty(name = "rag.chunk.paragraphs", defaultValue = "8") int paragraphsPerChunk,
                            @ConfigProperty(name = "rag.ingest.enabled", defaultValue = "true") boolean enabled) {
        this.store = store;
        this.embeddingModel = embeddingModel;
        this.sourceUrl = sourceUrl;
        this.paragraphsPerChunk = paragraphsPerChunk;
        this.enabled = enabled;
    }

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) {
            Log.info("Startup ingestion disabled via rag.ingest.enabled=false");
            return;
        }
        try {
            Log.infof("Fetching source document from %s", sourceUrl);
            String body = fetch(sourceUrl);

            List<String> chunks = splitIntoParagraphChunks(body, paragraphsPerChunk);
            Log.infof("Split document into %d chunk(s) of up to %d paragraph(s) each",
                    chunks.size(), paragraphsPerChunk);

            List<TextSegment> segments = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                TextSegment segment = TextSegment.from(chunks.get(i));
                segment.metadata().put("source", sourceUrl);
                segment.metadata().put("chunk_index", String.valueOf(i));
                segments.add(segment);
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            store.addAll(embeddings, segments);
            Log.infof("Ingested %d chunk(s) into Chroma", segments.size());
        } catch (Exception e) {
            Log.errorf(e, "Failed to ingest document from %s", sourceUrl);
            throw new RuntimeException("Startup ingestion failed", e);
        }
    }

    private String fetch(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("Unexpected status " + response.statusCode() + " fetching " + url);
        }
        return response.body();
    }

    static List<String> splitIntoParagraphChunks(String text, int paragraphsPerChunk) {
        String normalized = text.replace("\r\n", "\n");
        String[] rawParagraphs = normalized.split("\\n\\s*\\n");

        List<String> paragraphs = new ArrayList<>();
        for (String p : rawParagraphs) {
            String trimmed = p.strip();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }

        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i += paragraphsPerChunk) {
            int end = Math.min(i + paragraphsPerChunk, paragraphs.size());
            chunks.add(String.join("\n\n", paragraphs.subList(i, end)));
        }
        return chunks;
    }
}
