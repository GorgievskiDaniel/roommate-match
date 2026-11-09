package mk.roommatematch.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "app.uploads.dir=target/test-uploads")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class AiFeaturesIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @MockitoBean
    GeminiClient gemini;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private record User(String token, long id) {
    }

    private void modelAnswers(String answer) throws Exception {
        when(gemini.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree(answer));
    }

    private static String filters(String overrides) {
        return """
                {"understood":true,"placeKinds":[],"areas":[],"dorms":[],"minRent":null,"maxRent":null,
                 "noSmokers":false,"noPets":false,"residentGender":"ANY",%s"_":0}""".formatted(overrides);
    }

    @Test
    void searchFiltersRealListingsByWhatTheModelUnderstood() throws Exception {
        long cheap = listing("DEBAR", "APARTMENT", 100, false, false);
        long expensive = listing("DEBAR", "APARTMENT", 300, false, false);
        long smokers = listing("DEBAR", "APARTMENT", 100, true, false);
        long pets = listing("DEBAR", "APARTMENT", 100, false, true);
        long house = listing("DEBAR", "HOUSE", 100, false, false);
        long elsewhere = listing("RESEN", "APARTMENT", 100, false, false);
        modelAnswers(filters("""
                "placeKinds":["APARTMENT"],"areas":["DEBAR"],"maxRent":150,"noSmokers":true,"noPets":true,"""));

        search("соба во Дебар до 150 евра без пушачи и миленичиња")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.understood").value(true))
                .andExpect(jsonPath("$.listingIds", contains((int) cheap)))
                .andExpect(jsonPath("$.dormIds").isEmpty())
                .andExpect(jsonPath("$.filters", containsInAnyOrder(
                        "Стан", "Дебар", "Кирија до 150 €", "Без пушачи", "Без миленичиња")));
        assertThat(List.of(expensive, smokers, pets, house, elsewhere)).doesNotContain(cheap);
    }

    @Test
    void wholeSkopjeCoversItsMunicipalitiesAndLeavesDormsOutUnlessAskedFor() throws Exception {
        long inSkopje = listing("BUTEL", "HOUSE", 120, false, false);
        long notInSkopje = listing("VELES", "HOUSE", 120, false, false);
        modelAnswers(filters("\"areas\":[\"SKOPJE\"],"));

        search("нешто во Скопје")
                .andExpect(jsonPath("$.listingIds", hasItem((int) inSkopje)))
                .andExpect(jsonPath("$.listingIds", not(hasItem((int) notInSkopje))))
                .andExpect(jsonPath("$.dormIds").isEmpty());
    }

    @Test
    void dormsInSkopjeAreFoundWhenAskedFor() throws Exception {
        modelAnswers(filters("\"placeKinds\":[\"DORM\"],\"areas\":[\"SKOPJE\"],"));

        search("дом во Скопје").andExpect(jsonPath("$.dormIds", containsInAnyOrder(1, 2)));
    }

    @Test
    void dormSearchByTownReturnsOnlyDormsThere() throws Exception {
        modelAnswers(filters("\"placeKinds\":[\"DORM\"],\"areas\":[\"STIP\"],"));

        search("дом во Штип")
                .andExpect(jsonPath("$.listingIds").isEmpty())
                .andExpect(jsonPath("$.dormIds", containsInAnyOrder(5, 7, 8)));
    }

    @Test
    void namedDormIsFoundByItsExactName() throws Exception {
        modelAnswers(filters("\"dorms\":[\"ПСД Логос\"],"));

        search("логос").andExpect(jsonPath("$.dormIds", contains(8)));
    }

    @Test
    void valuesOutsideTheSchemaAreIgnored() throws Exception {
        modelAnswers(filters("\"areas\":[\"MARS\"],\"dorms\":[\"Хотел\"],\"maxRent\":-5,"));

        search("било што")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters").isEmpty());
    }

    @Test
    void searchThatIsNotAboutHousingFindsNothing() throws Exception {
        modelAnswers("{\"understood\":false}");

        search("која е престолнината на Франција")
                .andExpect(jsonPath("$.understood").value(false))
                .andExpect(jsonPath("$.listingIds").isEmpty())
                .andExpect(jsonPath("$.dormIds").isEmpty());
    }

    @Test
    void modelFailureIsReportedWithoutBreakingAnything() throws Exception {
        when(gemini.generateJson(anyString(), anyString(), any())).thenThrow(new AiUnavailableException("down"));

        search("стан").andExpect(status().isServiceUnavailable());
    }

    @Test
    void blankOrTooLongSearchNeverReachesTheModel() throws Exception {
        clearInvocations(gemini);

        send(post("/api/map/search"), newUser(), Map.of("query", "  ")).andExpect(status().isBadRequest());
        send(post("/api/map/search"), newUser(), Map.of("query", "а".repeat(301))).andExpect(status().isBadRequest());

        verify(gemini, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    void searchRequiresLogin() throws Exception {
        mvc.perform(post("/api/map/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"стан\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void explanationIsGeneratedOnceAndReusedUntilTheFactsChange() throws Exception {
        clearInvocations(gemini);
        when(gemini.generateText(anyString(), anyString())).thenReturn("И двајцата со {{IME}} сте непушачи.");
        Map<String, Object> profile = apartment("Марија", "SEARCH_TOGETHER", "KOCANI");
        User viewer = user(apartment("Ана", "SEARCH_TOGETHER", "KOCANI"));
        User other = user(profile);

        explain(viewer, other)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("И двајцата со Марија сте непушачи."));
        explain(viewer, other).andExpect(status().isOk());
        verify(gemini, times(1)).generateText(anyString(), anyString());

        profile.put("tidiness", 1);
        send(put("/api/profile/me"), other, profile).andExpect(status().isOk());

        explain(viewer, other).andExpect(status().isOk());
        verify(gemini, times(2)).generateText(anyString(), anyString());
    }

    @Test
    void renamingSomeoneUpdatesTheTextWithoutANewCall() throws Exception {
        clearInvocations(gemini);
        when(gemini.generateText(anyString(), anyString())).thenReturn("Добро се вклопуваш со {{IME}}.");
        Map<String, Object> profile = apartment("Марија", "SEARCH_TOGETHER", "KRUSEVO");
        User viewer = user(apartment("Ана", "SEARCH_TOGETHER", "KRUSEVO"));
        User other = user(profile);
        explain(viewer, other).andExpect(status().isOk());

        profile.put("firstName", "Маја");
        send(put("/api/profile/me"), other, profile).andExpect(status().isOk());

        explain(viewer, other).andExpect(jsonPath("$.text").value("Добро се вклопуваш со Маја."));
        verify(gemini, times(1)).generateText(anyString(), anyString());
    }

    @Test
    void modelNeverReceivesNamesOrBios() throws Exception {
        clearInvocations(gemini);
        when(gemini.generateText(anyString(), anyString())).thenReturn("Текст.");
        Map<String, Object> first = apartment("Уникатноиме", "SEARCH_TOGETHER", "VINICA");
        first.put("bio", "Тајниопис");
        Map<String, Object> second = apartment("Другоиме", "SEARCH_TOGETHER", "VINICA");
        User viewer = user(first);
        User other = user(second);

        explain(viewer, other).andExpect(status().isOk());

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(gemini).generateText(anyString(), input.capture());
        assertThat(input.getValue())
                .doesNotContain("Уникатноиме", "Другоиме", "Тајниопис", "@students.test", "2003")
                .contains("sharedHobbies", "tidinessOutOf5");
    }

    @Test
    void noExplanationForPeopleWhoDoNotMatch() throws Exception {
        clearInvocations(gemini);
        User viewer = user(apartment("Ана", "SEARCH_TOGETHER", "BOGDANCI"));
        User other = user(apartment("Вера", "SEARCH_TOGETHER", "PROBISTIP"));

        explain(viewer, other).andExpect(status().isNotFound());

        verify(gemini, never()).generateText(anyString(), anyString());
    }

    @Test
    void failedExplanationIsNotCached() throws Exception {
        clearInvocations(gemini);
        User viewer = user(apartment("Ана", "SEARCH_TOGETHER", "RADOVIS"));
        User other = user(apartment("Вера", "SEARCH_TOGETHER", "RADOVIS"));
        when(gemini.generateText(anyString(), anyString())).thenThrow(new AiUnavailableException("down"));
        explain(viewer, other).andExpect(status().isServiceUnavailable());

        org.mockito.Mockito.reset(gemini);
        when(gemini.generateText(anyString(), anyString())).thenReturn("Сега работи.");

        explain(viewer, other).andExpect(jsonPath("$.text").value("Сега работи."));
    }

    private ResultActions search(String query) throws Exception {
        return send(post("/api/map/search"), newUser(), Map.of("query", query));
    }

    private ResultActions explain(User viewer, User other) throws Exception {
        return mvc.perform(get("/api/matches/" + other.id() + "/explanation")
                .header("Authorization", "Bearer " + viewer.token()));
    }

    private long listing(String area, String type, int rent, boolean smoker, boolean pets) throws Exception {
        Map<String, Object> profile = apartment("Сопственик", "HAS_PLACE", area);
        profile.put("smoker", smoker);
        profile.put("hasPets", pets);
        User owner = user(profile);
        Map<String, Object> listing = new HashMap<>();
        listing.put("propertyType", type);
        listing.put("address", "Главна 1");
        listing.put("area", area);
        listing.put("latitude", 41.7);
        listing.put("longitude", 21.5);
        listing.put("monthlyRent", rent);
        listing.put("freeSpots", 1);
        listing.put("description", "Опис.");
        JsonNode created = json.readTree(send(post("/api/listings"), owner, listing)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();
        mvc.perform(multipart("/api/listings/" + id + "/images")
                        .file(new MockMultipartFile("images", "s.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk());
        return id;
    }

    private User newUser() throws Exception {
        return user(apartment("Корисник", "SEARCH_TOGETHER", "KARPOSH"));
    }

    private User user(Map<String, Object> profile) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", UUID.randomUUID() + "@students.test", "password", "lozinka123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).get("token").asText();
        User partial = new User(token, 0);
        send(put("/api/profile/me"), partial, profile).andExpect(status().isOk());
        String me = mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return new User(token, json.readTree(me).get("id").asLong());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, User user, Map<String, ?> body) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static Map<String, Object> apartment(String firstName, String situation, String area) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", firstName);
        profile.put("gender", "FEMALE");
        profile.put("birthDate", "2003-05-14");
        profile.put("faculty", "ФИНКИ");
        profile.put("bio", "");
        profile.put("housingType", "APARTMENT");
        profile.put("situation", situation);
        profile.put("budgetMin", 100);
        profile.put("budgetMax", 200);
        profile.put("areas", List.of(area));
        profile.put("propertyTypes", List.of("APARTMENT", "HOUSE"));
        profile.put("smoker", false);
        profile.put("hasPets", false);
        profile.put("guestsFrequency", "RARELY");
        profile.put("tidiness", 4);
        profile.put("sleepSchedule", "FLEXIBLE");
        profile.put("quietNeed", 3);
        profile.put("acceptsSmoker", true);
        profile.put("acceptsPets", true);
        profile.put("roommateGenderPreference", "SAME");
        profile.put("hobbyIds", List.of(1, 2));
        return profile;
    }
}
