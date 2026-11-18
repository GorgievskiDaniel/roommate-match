package mk.roommatematch.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import mk.roommatematch.support.RecordingMailer;
import mk.roommatematch.support.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Each check uses its own remote address, so the counts of one test do not spill into another. */
@SpringBootTest(properties = {"app.rate-limit.auth=5", "app.rate-limit.ai=2", "app.rate-limit.addresses=3"})
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class RateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    MockMvc mvc;

    @Autowired
    RecordingMailer mail;

    @Autowired
    ObjectMapper json;

    @Test
    void theSixthLoginAttemptInAMinuteIsRefused() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("10.0.0.1").andExpect(status().isUnauthorized());
        }
        login("10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").exists());
        // Someone else is not affected.
        login("10.0.0.2").andExpect(status().isUnauthorized());
    }

    @Test
    void languageModelRequestsAreCountedPerStudent() throws Exception {
        String first = TestAccounts.register(mvc, mail);
        String second = TestAccounts.register(mvc, mail);

        for (int i = 0; i < 2; i++) {
            ai(first).andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }
        ai(first).andExpect(status().isTooManyRequests());
        ai(second).andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }

    @Test
    void addressSuggestionsAreLimitedToo() throws Exception {
        String token = TestAccounts.register(mvc, mail);

        for (int i = 0; i < 3; i++) {
            addresses(token).andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }
        addresses(token).andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String address) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "nobody@students.test", "password", "pogresna123"))));
    }

    /** No profile, so the request stops early, but it still counts. */
    private ResultActions ai(String token) throws Exception {
        return mvc.perform(get("/api/matches/1/explanation").header("Authorization", "Bearer " + token));
    }

    private ResultActions addresses(String token) throws Exception {
        // A query too short to need the geocoder.
        return mvc.perform(get("/api/addresses").param("q", "a").header("Authorization", "Bearer " + token));
    }
}
