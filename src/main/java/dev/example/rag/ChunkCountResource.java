package dev.example.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/chunks")
public class ChunkCountResource {

    private final String resourcePath;
    private final int paragraphsPerChunk;

    public ChunkCountResource(
            @ConfigProperty(name = "rag.resource.path", defaultValue = "quantumpulse-3000.md") String resourcePath,
            @ConfigProperty(name = "rag.chunk.paragraphs", defaultValue = "8") int paragraphsPerChunk) {
        this.resourcePath = resourcePath;
        this.paragraphsPerChunk = paragraphsPerChunk;
    }

    @GET
    @Path("/count")
    @Produces(MediaType.APPLICATION_JSON)
    public Response count() throws IOException {
        String text = readClasspathResource(resourcePath);
        List<String> paragraphs = splitParagraphs(text);
        int chunkCount = (paragraphs.size() + paragraphsPerChunk - 1) / paragraphsPerChunk;
        return Response.ok(new CountResponse(
                resourcePath,
                paragraphsPerChunk,
                paragraphs.size(),
                chunkCount)).build();
    }

    private String readClasspathResource(String path) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<String> splitParagraphs(String text) {
        String normalized = text.replace("\r\n", "\n");
        String[] raw = normalized.split("\\n\\s*\\n");
        List<String> paragraphs = new ArrayList<>(raw.length);
        for (String p : raw) {
            String trimmed = p.strip();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    public record CountResponse(
            String source,
            int paragraphsPerChunk,
            int totalParagraphs,
            int chunkCount) {}
}
