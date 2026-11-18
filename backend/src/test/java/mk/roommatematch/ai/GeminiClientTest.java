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
        return client(key, List.of());
    }

    private GeminiClient client(String key, List<String> fallbackModels) {
        return new GeminiClient(builder.build(),
                new GeminiProperties(key, "model-x", fallbackModels, "embed-x", "https://gemini.test/v1beta", "2026-05-20",
                        Duration.ofSeconds(5), "low"),
                new ObjectMapper(), Duration.ZERO);
    }

    @Test
    void busyModelHandsOverToTheFallbackAtOnce() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("model-x"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("model-y"))
                .andExpect(jsonPath("$.system_instruction").value("a"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client("secret", List.of("model-y")).generateText("a", "b")).contains("maxRent");
        server.verify();
    }

    @Test
    void afterEveryModelFailedItStartsAgainFromTheFirst() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("model-x"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("model-y"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("model-x"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client("secret", List.of("model-y")).generateText("a", "b")).contains("maxRent");
        server.verify();
    }

    @Test
    void webSearchReadsTheAnswerItsSourcesAndTheSuggestions() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.tools[0].type").value("google_search"))
                .andExpect(jsonPath("$.store").value(false))
                .andRespond(withSuccess("""
                        {"steps":[
                          {"type":"google_search_call","arguments":{"queries":["дом Пелагонија"]}},
                          {"type":"google_search_result","result":[{"search_suggestions":"<div>чипови</div>"}]},
                          {"type":"model_output","content":[{"type":"text","text":"Се реновира.","annotations":[
                            {"type":"url_citation","url":"https://mon.gov.mk/a","title":"mon.gov.mk"},
                            {"type":"url_citation","url":"https://mon.gov.mk/a","title":"mon.gov.mk"},
                            {"type":"url_citation","url":"javascript:alert(1)","title":"лош"}]}]}
                        ]}""", MediaType.APPLICATION_JSON));

        GeminiClient.SearchAnswer answer = client("secret").searchWeb("a", "b").orElseThrow();

        assertThat(answer.text()).isEqualTo("Се реновира.");
        assertThat(answer.citations()).containsExactly(new GeminiClient.Citation("mon.gov.mk", "https://mon.gov.mk/a"));
        assertThat(answer.searchSuggestions()).isEqualTo("<div>чипови</div>");
        server.verify();
    }

    @Test
    void webSearchTriesEachModelOnceWithoutWaiting() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("model-x"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("model-y"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client("secret", List.of("model-y")).searchWeb("a", "b"))
                .isInstanceOf(AiUnavailableException.class);
        server.verify();
    }

    @Test
    void webSearchWithoutTextIsEmpty() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withSuccess("{\"steps\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client("secret").searchWeb("a", "b")).isEmpty();
    }

    @Test
    void badRequestDoesNotMoveToTheFallback() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(), requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.model").value("model-x"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client("secret", List.of("model-y")).generateText("a", "b"))
                .isInstanceOf(AiUnavailableException.class);
        server.verify();
    }

    @Test
    void briefOverloadIsTriedAgain() {
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client("secret").generateText("a", "b")).contains("maxRent");
        server.verify();
    }

    @Test
    void badRequestIsNotTriedAgain() {
        server.expect(org.springframework.test.web.client.ExpectedCount.once(), requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client("secret").generateText("a", "b")).isInstanceOf(AiUnavailableException.class);
        server.verify();
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
        server.expect(org.springframework.test.web.client.ExpectedCount.times(4), requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client("secret").generateText("a", "b")).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void overloadIsRetriedUpToThreeTimesWithTheConfiguredThinkingLevel() {
        server.expect(org.springframework.test.web.client.ExpectedCount.times(3), requestTo("https://gemini.test/v1beta/interactions"))
                .andExpect(jsonPath("$.generation_config.thinking_level").value("low"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://gemini.test/v1beta/interactions"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(client("secret").generateText("a", "b")).contains("maxRent");
        server.verify();
    }

    @Test
    void waitDoublesUpToFourSeconds() {
        Duration first = Duration.ofSeconds(1);

        assertThat(GeminiClient.backoff(first, 1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(GeminiClient.backoff(first, 2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(GeminiClient.backoff(first, 3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(GeminiClient.backoff(first, 4)).isEqualTo(Duration.ofSeconds(4));
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
