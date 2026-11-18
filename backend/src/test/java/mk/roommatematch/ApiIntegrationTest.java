package mk.roommatematch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import mk.roommatematch.support.RecordingMailer;
import mk.roommatematch.support.TestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
    RecordingMailer mail;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reference")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWaitsUntilTheEmailIsConfirmed() throws Exception {
        String email = uniqueEmail();

        send(post("/api/auth/register"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").doesNotExist());
        assertThat(mail.sentTo(email)).hasSize(1);

        send(post("/api/auth/login"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String link = mail.lastToken(email, "/verify-email");
        send(post("/api/auth/verify-email"), null, Map.of("token", link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        send(post("/api/auth/login"), null, Map.of("email", email.toUpperCase(), "password", "lozinka123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginToAnUnconfirmedAccountSendsAFreshLinkOncePerMinute() throws Exception {
        String email = uniqueEmail();
        send(post("/api/auth/register"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isCreated());
        // As if the first link was sent long ago and has expired.
        jdbc.update("""
                update account_tokens set created_at = now() - interval '2 days', expires_at = now() - interval '1 day'
                where user_id = (select id from users where email = ?)""", email);

        send(post("/api/auth/login"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isForbidden());
        send(post("/api/auth/login"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isForbidden());
        assertThat(mail.sentTo(email)).hasSize(2);

        send(post("/api/auth/verify-email"), null, Map.of("token", mail.lastToken(email, "/verify-email")))
                .andExpect(status().isOk());
    }

    @Test
    void confirmationLinkWorksOnlyOnce() throws Exception {
        String email = uniqueEmail();
        register(email, "lozinka123");

        send(post("/api/auth/verify-email"), null, Map.of("token", mail.lastToken(email, "/verify-email")))
                .andExpect(status().isBadRequest());
        send(post("/api/auth/verify-email"), null, Map.of("token", "izmislen"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordDoesNotRevealAnUnconfirmedAccount() throws Exception {
        String email = uniqueEmail();
        send(post("/api/auth/register"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isCreated());

        send(post("/api/auth/login"), null, Map.of("email", email, "password", "pogresna123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmationIsResentAtMostOncePerMinuteAndNeverToStrangers() throws Exception {
        String email = uniqueEmail();
        send(post("/api/auth/register"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isCreated());

        send(post("/api/auth/resend-confirmation"), null, Map.of("email", email)).andExpect(status().isNoContent());
        assertThat(mail.sentTo(email)).hasSize(1);

        String stranger = uniqueEmail();
        send(post("/api/auth/resend-confirmation"), null, Map.of("email", stranger)).andExpect(status().isNoContent());
        send(post("/api/auth/forgot-password"), null, Map.of("email", stranger)).andExpect(status().isNoContent());
        assertThat(mail.sentTo(stranger)).isEmpty();
    }

    @Test
    void newPasswordReplacesTheOldOneAndEndsOtherSessions() throws Exception {
        String email = uniqueEmail();
        String oldSession = register(email, "lozinka123");

        send(post("/api/auth/forgot-password"), null, Map.of("email", email)).andExpect(status().isNoContent());
        String link = mail.lastToken(email, "/reset-password");
        String body = send(post("/api/auth/reset-password"), null, Map.of("token", link, "password", "novalozinka1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newSession = json.readTree(body).get("token").asText();

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + oldSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + newSession)).andExpect(status().isOk());
        send(post("/api/auth/login"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isUnauthorized());
        send(post("/api/auth/login"), null, Map.of("email", email, "password", "novalozinka1"))
                .andExpect(status().isOk());
        send(post("/api/auth/reset-password"), null, Map.of("token", link, "password", "trecalozinka1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resettingThePasswordAlsoConfirmsTheEmail() throws Exception {
        String email = uniqueEmail();
        send(post("/api/auth/register"), null, Map.of("email", email, "password", "lozinka123"))
                .andExpect(status().isCreated());

        send(post("/api/auth/forgot-password"), null, Map.of("email", email)).andExpect(status().isNoContent());
        send(post("/api/auth/reset-password"), null,
                Map.of("token", mail.lastToken(email, "/reset-password"), "password", "novalozinka1"))
                .andExpect(status().isOk());

        send(post("/api/auth/login"), null, Map.of("email", email, "password", "novalozinka1"))
                .andExpect(status().isOk());
    }

    @Test
    void shortNewPasswordIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email, "lozinka123");
        send(post("/api/auth/forgot-password"), null, Map.of("email", email)).andExpect(status().isNoContent());

        send(post("/api/auth/reset-password"), null,
                Map.of("token", mail.lastToken(email, "/reset-password"), "password", "kratka"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("password")));
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
    void referenceListsTheNineDorms() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");

        mvc.perform(get("/api/reference").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dorms.length()").value(9))
                .andExpect(jsonPath("$.areas.length()").value(43))
                .andExpect(jsonPath("$.faculties[0].label")
                        .value("Факултет за информатички науки и компјутерско инженерство (ФИНКИ), Скопје"))
                .andExpect(jsonPath("$.faculties[0].latin").value("FINKI"));
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
    void dormProfileIsNotAskedAboutSmokingOrPets() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        Map<String, Object> dorm = apartmentProfile();
        dorm.put("housingType", "DORM");
        dorm.put("dormId", 3);
        dorm.put("smoker", null);
        dorm.put("hasPets", null);
        dorm.put("acceptsSmoker", null);
        dorm.put("acceptsPets", null);

        send(put("/api/profile/me"), token, dorm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smoker").isEmpty())
                .andExpect(jsonPath("$.hasPets").isEmpty())
                .andExpect(jsonPath("$.acceptsSmoker").isEmpty())
                .andExpect(jsonPath("$.acceptsPets").isEmpty());
    }

    @Test
    void dormProfileDropsSmokingAndPetsEvenIfSent() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        Map<String, Object> dorm = apartmentProfile();
        dorm.put("housingType", "DORM");
        dorm.put("dormId", 3);
        dorm.put("smoker", true);
        dorm.put("hasPets", true);

        send(put("/api/profile/me"), token, dorm).andExpect(status().isOk());
        dorm.put("housingType", "APARTMENT");
        dorm.put("dormId", null);
        dorm.put("smoker", null);

        send(put("/api/profile/me"), token, dorm)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("smoker")));
    }

    @Test
    void dormStudentPostsAnAdThatShowsOnTheDormPage() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        send(put("/api/profile/me"), token, dormProfile(4)).andExpect(status().isOk());

        send(put("/api/dorm-posts/mine"), token, Map.of("text", "  Барам цимерка од септември.  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Барам цимерка од септември."))
                .andExpect(jsonPath("$.dorm").value("ДСД Никола Карев"));

        mvc.perform(get("/api/dorms/4").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.seekers[0].post").value("Барам цимерка од септември."));
        mvc.perform(get("/api/dorm-posts/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anEmptyDormAdIsRejected() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        send(put("/api/profile/me"), token, dormProfile(4)).andExpect(status().isOk());

        send(put("/api/dorm-posts/mine"), token, Map.of("text", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("text")));
    }

    @Test
    void anyoneCanPostADormAdForTheDormTheyChoose() throws Exception {
        String flat = register(uniqueEmail(), "lozinka123");
        send(put("/api/profile/me"), flat, apartmentProfile()).andExpect(status().isOk());

        // A flat seeker has no dorm on the profile, so the ad has to name one.
        send(put("/api/dorm-posts/mine"), flat, Map.of("text", "Оглас"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("dormId")));
        send(put("/api/dorm-posts/mine"), flat, Map.of("dormId", 6, "text", "Барам цимерка во Тетово."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dorm").value("ДСД Тетово"));

        mvc.perform(get("/api/dorms/6").header("Authorization", "Bearer " + flat))
                .andExpect(jsonPath("$.seekers[0].post").value("Барам цимерка во Тетово."));

        // The ad stays when the profile changes.
        send(put("/api/profile/me"), flat, dormProfile(4)).andExpect(status().isOk());
        mvc.perform(get("/api/dorm-posts/mine").header("Authorization", "Bearer " + flat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dormId").value(6));
    }

    @Test
    void profilePictureCanBeAddedShownAndRemoved() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        send(put("/api/profile/me"), token, apartmentProfile())
                .andExpect(jsonPath("$.avatarUrl").isEmpty());
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        String body = mvc.perform(multipart("/api/profile/me/avatar")
                        .file(new MockMultipartFile("image", "ja.png", "image/png", png))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = json.readTree(body).get("avatarUrl").asText();
        assertThat(url).startsWith("/api/images/").endsWith(".png");
        mvc.perform(get(url)).andExpect(status().isOk());

        mvc.perform(multipart("/api/profile/me/avatar")
                        .file(new MockMultipartFile("image", "ne.png", "image/png", "не е слика".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/profile/me/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").isEmpty());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void aStudentWithoutAPlaceCanLookBothWays() throws Exception {
        String token = register(uniqueEmail(), "lozinka123");
        Map<String, Object> profile = apartmentProfile();
        profile.put("situation", "NEEDS_OR_TOGETHER");

        send(put("/api/profile/me"), token, profile)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situation").value("NEEDS_OR_TOGETHER"));
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
        return TestAccounts.register(mvc, mail, email, password);
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
        profile.put("cleaning", "REGULAR");
        profile.put("sleepSchedule", "FLEXIBLE");
        profile.put("sound", "QUIET_SPEAKER");
        profile.put("communication", "OCCASIONAL");
        profile.put("sharing", "BASICS");
        profile.put("parties", "SMALL");
        profile.put("partnerStays", "SOMETIMES");
        profile.put("acceptsSmoker", false);
        profile.put("acceptsPets", true);
        profile.put("roommateGenderPreference", "SAME");
        profile.put("hobbyIds", List.of(1, 2));
        return profile;
    }

    /** A dorm without room types, so no room has to be chosen. */
    private static Map<String, Object> dormProfile(long dormId) {
        Map<String, Object> profile = apartmentProfile();
        profile.put("housingType", "DORM");
        profile.put("dormId", dormId);
        profile.put("situation", null);
        profile.put("budgetMin", null);
        profile.put("budgetMax", null);
        profile.put("areas", List.of());
        profile.put("propertyTypes", List.of());
        return profile;
    }
}
