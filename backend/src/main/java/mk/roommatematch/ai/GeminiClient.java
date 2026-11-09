package mk.roommatematch.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GeminiClient {

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

    private String send(String systemInstruction, String input, Map<String, Object> responseFormat) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new AiUnavailableException("GEMINI_API_KEY is not set");
        }
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
