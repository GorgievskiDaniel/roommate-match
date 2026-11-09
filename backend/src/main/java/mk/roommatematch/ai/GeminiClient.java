package mk.roommatematch.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GeminiClient {

    /** Must match the vector column in the rag_chunks table. */
    private static final int EMBEDDING_DIMENSIONS = 768;

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper json;

    public GeminiClient(RestClient geminiRestClient, GeminiProperties properties, ObjectMapper json) {
        this.restClient = geminiRestClient;
        this.properties = properties;
        this.json = json;
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
        try {
            JsonNode response = restClient.post()
                    .uri(properties.baseUrl() + "/" + model + ":batchEmbedContents")
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("requests", requests))
                    .retrieve()
                    .body(JsonNode.class);
            return vectors(response, texts.size());
        } catch (RestClientException e) {
            throw new AiUnavailableException("The call to the embedding model failed", e);
        }
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

    private void requireKey() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiUnavailableException("GEMINI_API_KEY is not set");
        }
    }

    private String send(String systemInstruction, String input, Map<String, Object> responseFormat) {
        requireKey();
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.model());
        // Nothing about students should stay stored on the provider's side longer than needed.
        body.put("store", false);
        body.put("system_instruction", systemInstruction);
        body.put("input", input);
        body.put("generation_config", Map.of("thinking_level", "minimal"));
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }
        try {
            JsonNode response = restClient.post()
                    .uri(properties.baseUrl() + "/interactions")
                    .header("x-goog-api-key", properties.apiKey())
                    .header("Api-Revision", properties.apiRevision())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return outputText(response);
        } catch (RestClientException e) {
            throw new AiUnavailableException("The call to the model failed", e);
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
