package mk.roommatematch.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import mk.roommatematch.config.SessionCookie;
import mk.roommatematch.support.RecordingMailer;
import mk.roommatematch.support.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class AccountSessionIntegrationTest {

    private static final String PASSWORD = "lozinka123";

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
    void loginSetsAnHttpOnlyCookieThatSignsTheBrowserIn() throws Exception {
        String email = account();

        MvcResult login = login(email, PASSWORD).andExpect(status().isOk())
                .andExpect(cookie().httpOnly(SessionCookie.NAME, true))
                .andReturn();
        String header = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).contains("SameSite=Strict").contains("Path=/");
        Cookie session = login.getResponse().getCookie(SessionCookie.NAME);

        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void anOldCookieDoesNotStandInTheWayOfLoggingIn() throws Exception {
        String email = account();

        mvc.perform(post("/api/auth/login").cookie(new Cookie(SessionCookie.NAME, "expired.or.broken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void logoutEndsTheSessionOnTheServerAndClearsTheCookie() throws Exception {
        String email = account();
        String token = token(login(email, PASSWORD));
        String otherDevice = token(login(email, PASSWORD));

        mvc.perform(post("/api/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SessionCookie.NAME, 0));

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + otherDevice))
                .andExpect(status().isUnauthorized());
        // Logging in again works as usual.
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token(login(email, PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void deletingTheAccountNeedsThePasswordAndCannotBeUndone() throws Exception {
        String email = account();
        String token = token(login(email, PASSWORD));

        send(delete("/api/me"), token, Map.of("password", "pogresna123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        send(delete("/api/me"), token, Map.of("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SessionCookie.NAME, 0));

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        // The address is free again for a new account.
        send(post("/api/auth/register"), null, Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isCreated());
    }

    private String account() throws Exception {
        String email = UUID.randomUUID() + "@students.test";
        TestAccounts.register(mvc, mail, email, PASSWORD);
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return send(post("/api/auth/login"), null, Map.of("email", email, "password", password));
    }

    private String token(ResultActions login) throws Exception {
        return json.readTree(login.andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Map<String, ?> body)
            throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
