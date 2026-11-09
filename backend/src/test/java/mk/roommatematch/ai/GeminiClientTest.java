package mk.roommatematch.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiClientTest {

    private static final String REPLY = """
            {"status":"completed","steps":[
              {"type":"thought","signature":"x"},
              {"type":"model_output","content":[{"type":"text","text":"{\\"maxRent\\": 150}"}]}
            ]}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GeminiClient client(String key) {
        return new GeminiClient(builder.build(),
                new GeminiProperties(key, "model-x", "embed-x", "https://gemini.test/v1beta", "2026-05-20", Duration.ofSeconds(5)),
                new ObjectMapper());
    }

    @Test
    void sendsInstructionAndSchemaAndReadsTheModelOutput() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret"))
                .andExpect(jsonPath("$.model").value("model-x"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.system_instruction").value("instrukcija"))
                .andExpect(jsonPath("$.input").value("соба до 150"))
                .andExpect(jsonPath("$.response_format.mime_type").value("application/json"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client("secret").generateJson("instrukcija", "соба до 150", Map.of("type", "object"))
                .get("maxRent").asInt()).isEqualTo(150);
        server.verify();
    }

    @Test
    void embedsABatchAndKeepsTheOrder() {
        server.expect(requestTo("https://gemini.test/v1beta/models/embed-x:batchEmbedContents"))
                .andExpect(header("x-goog-api-key", "secret"))
                .andExpect(jsonPath("$.requests[1].model").value("models/embed-x"))
                .andExpect(jsonPath("$.requests[1].content.parts[0].text").value("второ"))
                .andExpect(jsonPath("$.requests[1].output_dimensionality").value(768))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[0.1,0.2]},{\"values\":[0.3,0.4]}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client("secret").embed(List.of("прво", "второ")))
                .containsExactly(new double[] {0.1, 0.2}, new double[] {0.3, 0.4});
        server.verify();
    }

    @Test
    void wrongNumberOfEmbeddingsBecomesUnavailable() {
        server.expect(requestTo("https://gemini.test/v1beta/models/embed-x:batchEmbedContents"))
                .andRespond(withSuccess("{\"embeddings\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("secret").embed(List.of("прво"))).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void missingKeyFailsWithoutCallingTheModel() {
        assertThatThrownBy(() -> client("").generateText("a", "b")).isInstanceOf(AiUnavailableException.class);
        server.verify();
    }

    @Test
    void errorFromTheModelBecomesUnavailable() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client("secret").generateText("a", "b")).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void replyWithoutTextBecomesUnavailable() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withSuccess("{\"steps\":[{\"type\":\"thought\"}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("secret").generateText("a", "b")).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void invalidJsonBecomesUnavailable() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withSuccess("{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"text\":\"ne e json {\"}]}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client("secret").generateJson("a", "b", Map.of()))
                .isInstanceOf(AiUnavailableException.class);
    }
}
