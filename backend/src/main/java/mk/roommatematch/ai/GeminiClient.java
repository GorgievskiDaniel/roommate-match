package mk.roommatematch.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    /** Must match the vector column in the rag_chunks table. */
    private static final int EMBEDDING_DIMENSIONS = 768;
    /** The first try and three retries. With one model the waits are 1, 2 and 4 seconds. */
    private static final int MAX_ATTEMPTS = 4;
    private static final Duration MAX_WAIT = Duration.ofSeconds(4);

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper json;
    private final Duration retryDelay;

    @Autowired
    public GeminiClient(RestClient geminiRestClient, GeminiProperties properties, ObjectMapper json) {
        this(geminiRestClient, properties, json, Duration.ofSeconds(1));
    }

    GeminiClient(RestClient geminiRestClient, GeminiProperties properties, ObjectMapper json, Duration retryDelay) {
        this.restClient = geminiRestClient;
        this.properties = properties;
        this.json = json;
        this.retryDelay = retryDelay;
    }

    /** Asks for an answer that follows the given JSON schema. */
    public JsonNode generateJson(String systemInstruction, String input, Map<String, Object> schema) {
        Map<String, Object> format = Map.of("type", "text", "mime_type", "application/json", "schema", schema);
        String text = send(systemInstruction, input, format);
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            throw new AiUnavailableException("The model did not return valid JSON", e);
        }
    }

    public String generateText(String systemInstruction, String input) {
        return send(systemInstruction, input, null);
    }

    /** One vector per text, in the same order. */
    public List<double[]> embed(List<String> texts) {
        requireKey();
        String model = "models/" + properties.embeddingModel();
        List<Map<String, Object>> requests = texts.stream()
                .<Map<String, Object>>map(text -> Map.of(
                        "model", model,
                        "content", Map.of("parts", List.of(Map.of("text", text))),
                        "output_dimensionality", EMBEDDING_DIMENSIONS))
                .toList();
        // Stored vectors only match vectors from the same model, so there is nothing to fall back to.
        JsonNode response = withRetries(List.of(model), MAX_ATTEMPTS, ignored -> restClient.post()
                .uri(properties.baseUrl() + "/" + model + ":batchEmbedContents")
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("requests", requests))
                .retrieve()
                .body(JsonNode.class));
        return vectors(response, texts.size());
    }

    private static List<double[]> vectors(JsonNode response, int expected) {
        List<double[]> result = new ArrayList<>();
        if (response != null) {
            for (JsonNode embedding : response.path("embeddings")) {
                double[] vector = new double[embedding.path("values").size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = embedding.path("values").get(i).asDouble();
                }
                result.add(vector);
            }
        }
        if (result.size() != expected) {
            throw new AiUnavailableException("The embedding model returned the wrong number of vectors");
        }
        return result;
    }

    /** The wait doubles after each failed attempt, up to four seconds. */
    static Duration backoff(Duration first, int failedAttempts) {
        Duration wait = first.multipliedBy(1L << Math.min(failedAttempts - 1, 10));
        return wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait;
    }

    private void requireKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiUnavailableException("GEMINI_API_KEY is not set");
        }
    }

    /** A web page the answer relies on. */
    public record Citation(String title, String url) {
    }

    /**
     * An answer grounded in a Google search.
     *
     * @param searchSuggestions HTML that Google's terms require to be shown next to the answer
     */
    public record SearchAnswer(String text, List<Citation> citations, String searchSuggestions) {
    }

    /**
     * Lets the model search Google before answering. Search has its own quota, which a pause does not
     * restore, so each model is tried only once.
     */
    public Optional<SearchAnswer> searchWeb(String systemInstruction, String input) {
        requireKey();
        Map<String, Object> body = body(systemInstruction, input);
        body.put("tools", List.of(Map.of("type", "google_search")));
        JsonNode response = interact(body, properties.models().size());
        StringBuilder text = new StringBuilder();
        Map<String, Citation> citations = new LinkedHashMap<>();
        StringBuilder suggestions = new StringBuilder();
        for (JsonNode step : response == null ? json.createArrayNode() : response.path("steps")) {
            String type = step.path("type").asText();
            if ("model_output".equals(type)) {
                for (JsonNode part : step.path("content")) {
                    text.append(part.path("text").asText(""));
                    for (JsonNode note : part.path("annotations")) {
                        String url = note.path("url").asText("");
                        if ("url_citation".equals(note.path("type").asText()) && url.startsWith("https://")) {
                            citations.putIfAbsent(url, new Citation(note.path("title").asText(url), url));
                        }
                    }
                }
            } else if ("google_search_result".equals(type)) {
                step.path("result").forEach(result -> suggestions.append(result.path("search_suggestions").asText("")));
            }
        }
        if (text.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SearchAnswer(text.toString().trim(), List.copyOf(citations.values()), suggestions.toString()));
    }

    private String send(String systemInstruction, String input, Map<String, Object> responseFormat) {
        requireKey();
        Map<String, Object> body = body(systemInstruction, input);
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }
        return outputText(interact(body, MAX_ATTEMPTS));
    }

    private Map<String, Object> body(String systemInstruction, String input) {
        Map<String, Object> body = new HashMap<>();
        // Nothing about students should stay stored on the provider's side longer than needed.
        body.put("store", false);
        body.put("system_instruction", systemInstruction);
        body.put("input", input);
        body.put("generation_config", Map.of("thinking_level", properties.thinkingLevel()));
        return body;
    }

    private JsonNode interact(Map<String, Object> body, int maxAttempts) {
        return withRetries(properties.models(), maxAttempts, model -> {
            Map<String, Object> request = new HashMap<>(body);
            request.put("model", model);
            return restClient.post()
                    .uri(properties.baseUrl() + "/interactions")
                    .header("x-goog-api-key", properties.apiKey())
                    .header("Api-Revision", properties.apiRevision())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        });
    }

    /**
     * Models are often briefly overloaded or over the free limit. Then the next model is tried at once,
     * and only after every model has failed is there a pause before starting again from the first. A
     * mistake in the request itself is not tried again.
     */
    private JsonNode withRetries(List<String> models, int maxAttempts, Function<String, JsonNode> call) {
        int next = 0;
        int rounds = 0;
        for (int attempt = 1; ; attempt++) {
            String model = models.get(next);
            try {
                return call.apply(model);
            } catch (RestClientException e) {
                boolean temporary = !(e instanceof RestClientResponseException response)
                        || response.getStatusCode().is5xxServerError() || response.getStatusCode().value() == 429;
                if (!temporary || attempt == maxAttempts) {
                    throw new AiUnavailableException("The call to the model failed", e, temporary);
                }
                next = (next + 1) % models.size();
                log.info("{} is not available ({}), trying {}", model, String.valueOf(e.getMessage()).lines().findFirst().orElse(""),
                        models.get(next));
                if (next == 0) {
                    rounds++;
                    pause(backoff(retryDelay, rounds), e);
                }
            }
        }
    }

    private static void pause(Duration wait, RestClientException cause) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrupted while waiting to retry", cause);
        }
    }

    private static String outputText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        if (response != null) {
            for (JsonNode step : response.path("steps")) {
                if ("model_output".equals(step.path("type").asText())) {
                    step.path("content").forEach(part -> text.append(part.path("text").asText("")));
                }
            }
        }
        if (text.toString().isBlank()) {
            throw new AiUnavailableException("The model returned no text");
        }
        return text.toString().trim();
    }
}
