package mk.roommatematch.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Signs up a new student the way a real one does: register, then open the link from the e-mail. */
public final class TestAccounts {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TestAccounts() {
    }

    /** Returns the token of a new, confirmed account. */
    public static String register(MockMvc mvc, RecordingMailer mail) throws Exception {
        return register(mvc, mail, UUID.randomUUID() + "@students.test", "lozinka123");
    }

    public static String register(MockMvc mvc, RecordingMailer mail, String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isCreated());
        String link = mail.lastToken(email, "/verify-email");
        String body = mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("token", link))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("token").asText();
    }
}
