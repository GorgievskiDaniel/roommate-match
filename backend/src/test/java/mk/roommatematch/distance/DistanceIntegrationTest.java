package mk.roommatematch.distance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mk.roommatematch.ai.GeminiClient;
import mk.roommatematch.distance.RouteClient.Mode;
import mk.roommatematch.distance.RouteClient.Route;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class DistanceIntegrationTest {

    private static final long GOCE_DELCEV = 1;
    private static final long KOCO_RACIN = 3;
    private static final long FINKI = 1;
    private static final long TF_BITOLA = 18;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @MockitoBean
    RouteClient routes;

    @MockitoBean
    GeminiClient gemini;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void setUp() {
        clearInvocations(routes, gemini);
        when(routes.route(eq(Mode.WALKING), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new Route(2360, 1890));
        when(routes.route(eq(Mode.DRIVING), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new Route(2640, 318));
    }

    private void modelAnswers(String answer) throws Exception {
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree(answer));
    }

    @Test
    void facultiesIncludeTheirTown() throws Exception {
        authed(get("/api/faculties"), token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24))
                .andExpect(jsonPath("$[0].name").value("ФИНКИ (Скопје)"))
                .andExpect(jsonPath("$[0].latitude").value(42.0041))
                .andExpect(jsonPath("$[0].longitude").value(21.40974));
    }

    @Test
    void dormDistanceReportsBothWaysOfGettingThere() throws Exception {
        authed(get("/api/dorms/" + GOCE_DELCEV + "/distance?facultyId=" + FINKI), token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faculty").value("ФИНКИ (Скопје)"))
                .andExpect(jsonPath("$.walking.kilometers").value(2.4))
                .andExpect(jsonPath("$.walking.minutes").value(32))
                .andExpect(jsonPath("$.driving.kilometers").value(2.6))
                .andExpect(jsonPath("$.driving.minutes").value(5));
    }

    @Test
    void placesInAnotherTownAreNotOfferedOnFoot() throws Exception {
        authed(get("/api/dorms/" + GOCE_DELCEV + "/distance?facultyId=" + TF_BITOLA), token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walking").isEmpty());

        verify(routes, never()).route(eq(Mode.WALKING), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void unknownFacultyOrDormIsNotFound() throws Exception {
        String token = token();

        authed(get("/api/dorms/" + GOCE_DELCEV + "/distance?facultyId=9999"), token).andExpect(status().isNotFound());
        authed(get("/api/dorms/9999/distance?facultyId=" + FINKI), token).andExpect(status().isNotFound());
    }

    @Test
    void listingWithoutPhotosStaysHidden() throws Exception {
        String owner = token();
        Map<String, Object> listing = new HashMap<>();
        listing.put("propertyType", "APARTMENT");
        listing.put("address", "Главна 1");
        listing.put("area", "KARPOSH");
        listing.put("latitude", 42.0);
        listing.put("longitude", 21.4);
        listing.put("monthlyRent", 150);
        listing.put("freeSpots", 1);
        listing.put("description", "Опис.");
        long id = json.readTree(send(post("/api/listings"), owner, listing).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        authed(get("/api/listings/" + id + "/distance?facultyId=" + FINKI), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driving.minutes").value(5));
        authed(get("/api/listings/" + id + "/distance?facultyId=" + FINKI), token()).andExpect(status().isNotFound());
    }

    @Test
    void assistantAnswersADistanceQuestionWithCalculatedNumbers() throws Exception {
        modelAnswers("{\"intent\":\"DISTANCE\",\"dorm\":\"ДСД Гоце Делчев\",\"faculty\":\"FINKI\"}");

        ask("Колку е далеку ФИНКИ од Гоце Делчев?")
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.answer").value(
                        "Од ДСД Гоце Делчев до ФИНКИ (Скопје) има околу 2.6 km со кола, приближно 5 минути."
                                + " Пеш се 2.4 km, приближно 32 минути."))
                .andExpect(jsonPath("$.sources[0]").value("OpenStreetMap"));

        verify(gemini, never()).embed(anyList());
    }

    @Test
    void assistantFindsTheNearestDormToAFaculty() throws Exception {
        modelAnswers("{\"intent\":\"NEAREST_DORM\",\"dorm\":\"NONE\",\"faculty\":\"TFB\"}");

        ask("Кој дом е најблиску до Техничкиот факултет во Битола?")
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.startsWith("Најблиску е")));

        verify(routes, org.mockito.Mockito.atMost(6)).route(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void questionsThatAreNotAboutDistanceSkipTheExtraModelCall() throws Exception {
        when(gemini.embed(anyList())).thenReturn(List.of(new double[768]));

        ask("Кога е конкурсот?").andExpect(status().isOk());

        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    void anUnknownFacultyFallsBackToTheDocuments() throws Exception {
        modelAnswers("{\"intent\":\"DISTANCE\",\"dorm\":\"ДСД Кочо Рацин\",\"faculty\":\"NONE\"}");
        when(gemini.embed(anyList())).thenReturn(List.of(new double[768]));

        ask("Колку е далеку некој друг факултет од Кочо Рацин?")
                .andExpect(jsonPath("$.found").value(false));

        verify(routes, never()).route(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        authed(get("/api/dorms/" + KOCO_RACIN + "/distance?facultyId=" + TF_BITOLA), token()).andExpect(status().isOk());
    }

    @Test
    void distancesRequireLogin() throws Exception {
        mvc.perform(get("/api/faculties")).andExpect(status().isUnauthorized());
    }

    private ResultActions ask(String question) throws Exception {
        return send(post("/api/assistant"), token(), Map.of("question", question));
    }

    private ResultActions authed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                 String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String token, Map<String, ?> body) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    /** A user with a place of their own, so the same user can also create a listing. */
    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", UUID.randomUUID() + "@students.test", "password", "lozinka123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asText();
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", "Ана");
        profile.put("gender", "FEMALE");
        profile.put("birthDate", "2003-05-14");
        profile.put("faculty", "ФИНКИ");
        profile.put("bio", "");
        profile.put("housingType", "APARTMENT");
        profile.put("situation", "HAS_PLACE");
        profile.put("budgetMin", 100);
        profile.put("budgetMax", 200);
        profile.put("areas", List.of("KARPOSH"));
        profile.put("propertyTypes", List.of("APARTMENT"));
        profile.put("smoker", false);
        profile.put("hasPets", false);
        profile.put("guestsFrequency", "RARELY");
        profile.put("tidiness", 4);
        profile.put("sleepSchedule", "FLEXIBLE");
        profile.put("quietNeed", 3);
        profile.put("acceptsSmoker", false);
        profile.put("acceptsPets", false);
        profile.put("roommateGenderPreference", "SAME");
        profile.put("hobbyIds", List.of(1, 2));
        send(put("/api/profile/me"), token, profile).andExpect(status().isOk());
        return token;
    }
}
