package mk.roommatematch;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reference")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenLoginWithNormalizedEmail() throws Exception {
        String email = uniqueEmail();
        register(email, "lozinka123");

        send(post("/api/auth/login"), null, Map.of("email", email.toUpperCase(), "password", "lozinka123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email, "lozinka123");

        send(post("/api/auth/register"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email, "lozinka123");

        send(post("/api/auth/login"), null, Map.of("email", email, "password", "pogresna123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cyrillicPasswordOverBcryptLimitIsRejectedNotAServerError() throws Exception {
        String longPassword = "ш".repeat(40);

        send(post("/api/auth/register"), null, Map.of("email", uniqueEmail(), "password", longPassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("password")));
    }

    @Test
    void referenceListsTheEightDorms() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");

        mvc.perform(get("/api/reference").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dorms.length()").value(8))
                .andExpect(jsonPath("$.areas.length()").value(43));
    }

    @Test
    void profileCompletedFlagChangesAfterSaving() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.profileCompleted").value(false));
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        send(put("/api/profile/me"), token, apartmentProfile()).andExpect(status().isOk());

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.profileCompleted").value(true));
    }

    @Test
    void switchingToDormClearsApartmentFields() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        send(put("/api/profile/me"), token, apartmentProfile()).andExpect(status().isOk());

        Map<String, Object> dorm = apartmentProfile();
        dorm.put("housingType", "DORM");
        dorm.put("dormId", 3);

        send(put("/api/profile/me"), token, dorm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dormId").value(3))
                .andExpect(jsonPath("$.situation").isEmpty())
                .andExpect(jsonPath("$.budgetMin").isEmpty())
                .andExpect(jsonPath("$.areas").isEmpty())
                .andExpect(jsonPath("$.propertyTypes").isEmpty());
    }

    @Test
    void apartmentProfileKeepsTheChosenPropertyTypes() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");

        send(put("/api/profile/me"), token, apartmentProfile())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyTypes.length()").value(1))
                .andExpect(jsonPath("$.propertyTypes[0]").value("HOUSE"));
    }

    @Test
    void apartmentProfileRequiresAPropertyType() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        Map<String, Object> profile = apartmentProfile();
        profile.put("propertyTypes", List.of());

        send(put("/api/profile/me"), token, profile)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("propertyTypes")));
    }

    @Test
    void dormProfileRequiresDorm() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        Map<String, Object> profile = apartmentProfile();
        profile.put("housingType", "DORM");

        send(put("/api/profile/me"), token, profile)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("dormId")));
    }

    @Test
    void apartmentProfileValidatesBudgetAndAreas() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        Map<String, Object> profile = apartmentProfile();
        profile.put("budgetMin", 300);
        profile.put("budgetMax", 100);
        profile.put("areas", List.of());

        send(put("/api/profile/me"), token, profile)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("budgetMax")))
                .andExpect(jsonPath("$.errors", hasKey("areas")));
    }

    @Test
    void underageUserIsRejected() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        Map<String, Object> profile = apartmentProfile();
        profile.put("birthDate", LocalDate.now().minusYears(17).toString());

        send(put("/api/profile/me"), token, profile)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("birthDate")));
    }

    private String register(String email, String password) throws Exception {
        String body = send(post("/api/auth/register"), null, Map.of("email", email, "password", password))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Map<String, ?> body)
            throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@students.test";
    }

    private static Map<String, Object> apartmentProfile() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", "Ана");
        profile.put("gender", "FEMALE");
        profile.put("birthDate", "2003-05-14");
        profile.put("faculty", "ФИНКИ");
        profile.put("bio", "Студентка во втора година.");
        profile.put("housingType", "APARTMENT");
        profile.put("situation", "NEEDS_PLACE");
        profile.put("budgetMin", 100);
        profile.put("budgetMax", 200);
        profile.put("areas", List.of("AERODROM", "KARPOSH"));
        profile.put("propertyTypes", List.of("HOUSE"));
        profile.put("smoker", false);
        profile.put("hasPets", false);
        profile.put("guestsFrequency", "RARELY");
        profile.put("tidiness", 4);
        profile.put("sleepSchedule", "FLEXIBLE");
        profile.put("quietNeed", 3);
        profile.put("acceptsSmoker", false);
        profile.put("acceptsPets", true);
        profile.put("roommateGenderPreference", "SAME");
        profile.put("hobbyIds", List.of(1, 2));
        return profile;
    }
}
