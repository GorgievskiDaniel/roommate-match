package mk.roommatematch.matching;

import static org.assertj.core.api.Assertions.assertThat;
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

@SpringBootTest(properties = "app.uploads.dir=target/test-uploads")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class MatchIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

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
    void matchesRequireAProfile() throws Exception {
        authed(get("/api/matches"), register()).andExpect(status().isNotFound());
    }

    @Test
    void matchesAreSortedBestFirstAndHidePrivateData() throws Exception {
        // A town of its own, so results from other tests stay out of this list.
        Map<String, Object> me = apartment("SEARCH_TOGETHER", "VINICA");
        String token = user(me);
        Map<String, Object> worse = apartment("SEARCH_TOGETHER", "VINICA");
        worse.put("firstName", "Послаб");
        worse.put("cleaning", "WHEN_POSSIBLE");
        worse.put("guestsFrequency", "OFTEN");
        user(worse);
        Map<String, Object> better = apartment("SEARCH_TOGETHER", "VINICA");
        better.put("firstName", "Подобар");
        user(better);

        authed(get("/api/matches"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].profile.firstName").value("Подобар"))
                .andExpect(jsonPath("$[0].score.total").value(100))
                .andExpect(jsonPath("$[1].profile.firstName").value("Послаб"))
                .andExpect(jsonPath("$[0].profile.email").doesNotExist())
                .andExpect(jsonPath("$[0].profile.birthDate").doesNotExist());
    }

    @Test
    void seekerSeesThePlaceOfSomeoneWithAPhotographedListing() throws Exception {
        String owner = user(apartment("HAS_PLACE", "BEROVO"));
        String seeker = user(apartment("NEEDS_PLACE", "BEROVO"));
        JsonNode listing = json.readTree(send(post("/api/listings"), owner, listing("BEROVO"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = listing.get("id").asLong();

        // The rent and the place come from the listing; without a photo it is not shown yet.
        authed(get("/api/matches"), seeker)
                .andExpect(jsonPath("$[0].listingId").isEmpty())
                .andExpect(jsonPath("$[0].score.rent").value(100));

        MockMultipartFile image = new MockMultipartFile("images", "stan.png", "image/png", PNG);
        authed(multipart("/api/listings/" + id + "/images").file(image), owner).andExpect(status().isOk());

        authed(get("/api/matches"), seeker).andExpect(jsonPath("$[0].listingId").value((int) id));
    }

    @Test
    void inGoceDelcevADifferentRoomTypeKeepsTheScoreButComesLast() throws Exception {
        JsonNode goce = dorms().get(0);
        long dormId = goce.get("id").asLong();
        long room = goce.get("roomOptions").get(0).get("id").asLong();
        long otherRoom = goce.get("roomOptions").get(1).get("id").asLong();
        String token = user(dormProfile(dormId, room));
        // Different faculties, so only the room type and habits tell them apart.
        Map<String, Object> differentRoom = dormProfile(dormId, otherRoom);
        differentRoom.put("firstName", "Друга соба");
        differentRoom.put("faculty", "ФЕИТ");
        user(differentRoom);
        Map<String, Object> sameRoom = dormProfile(dormId, room);
        sameRoom.put("firstName", "Иста соба");
        sameRoom.put("faculty", "ФЕИТ");
        sameRoom.put("cleaning", "WHEN_POSSIBLE");
        user(sameRoom);

        JsonNode matches = json.readTree(authed(get("/api/matches"), token)
                .andReturn().getResponse().getContentAsString());
        int same = indexOf(matches, "Иста соба");
        int different = indexOf(matches, "Друга соба");

        assertThat(matches.get(different).get("score").get("total").asInt()).isEqualTo(100);
        assertThat(matches.get(different).get("score").get("differentRoomType").asBoolean()).isTrue();
        assertThat(matches.get(same).get("score").get("total").asInt()).isLessThan(100);
        assertThat(same).isLessThan(different);
    }

    @Test
    void stivNaumovDoesNotCareAboutTheRoomType() throws Exception {
        assertDormScore(dorms().get(1), 100, false);
    }

    @Test
    void blockedStudentsDisappearFromEachOthersMatches() throws Exception {
        String me = user(apartment("SEARCH_TOGETHER", "DEMIR_HISAR"));
        String other = user(apartment("SEARCH_TOGETHER", "DEMIR_HISAR"));
        authed(get("/api/matches"), me).andExpect(jsonPath("$.length()").value(1));

        authed(post("/api/blocks/" + id(other)), me).andExpect(status().isNoContent());

        authed(get("/api/matches"), me).andExpect(jsonPath("$.length()").value(0));
        authed(get("/api/matches"), other).andExpect(jsonPath("$.length()").value(0));
        authed(get("/api/matches/" + id(me) + "/explanation"), other).andExpect(status().isNotFound());
    }

    @Test
    void aListingOfSomeoneWhoNeedsAPlaceIsNotOfferedToOthers() throws Exception {
        String needs = user(apartment("NEEDS_PLACE", "KRATOVO"));
        String owner = user(apartment("HAS_PLACE", "KRATOVO"));
        send(post("/api/listings"), owner, listing("KRATOVO")).andExpect(status().isCreated());
        Map<String, Object> expensive = listing("KRATOVO");
        expensive.put("monthlyRent", 1000);
        JsonNode listing = json.readTree(send(post("/api/listings"), needs, expensive)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        MockMultipartFile image = new MockMultipartFile("images", "stan.png", "image/png", PNG);
        authed(multipart("/api/listings/" + listing.get("id").asLong() + "/images").file(image), needs)
                .andExpect(status().isOk());

        // The rent of that listing would be far over the owner's budget; it is not what the student offers.
        authed(get("/api/matches"), owner)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].listingId").isEmpty())
                .andExpect(jsonPath("$[0].score.rent").value(100));
    }

    @Test
    void aRentMoreThanTwentyPercentOverTheBudgetIsNoMatch() throws Exception {
        String seeker = user(apartment("NEEDS_PLACE", "KRUSEVO"));
        String owner = user(apartment("HAS_PLACE", "KRUSEVO"));
        Map<String, Object> place = listing("KRUSEVO");
        place.put("monthlyRent", 241);
        send(post("/api/listings"), owner, place).andExpect(status().isCreated());

        authed(get("/api/matches"), seeker).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void someoneWithAPlaceGivesNoRentOrAreasAndIsMatchedOnceTheListingIsUp() throws Exception {
        String seeker = user(apartment("NEEDS_PLACE", "KICEVO"));
        Map<String, Object> owner = apartment("HAS_PLACE", "KICEVO");
        owner.remove("budgetMin");
        owner.remove("budgetMax");
        owner.put("areas", List.of());
        String ownerToken = user(owner);
        authed(get("/api/profile/me"), ownerToken)
                .andExpect(jsonPath("$.budgetMin").isEmpty())
                .andExpect(jsonPath("$.areas.length()").value(0));

        authed(get("/api/matches"), seeker).andExpect(jsonPath("$.length()").value(0));

        send(post("/api/listings"), ownerToken, listing("KICEVO")).andExpect(status().isCreated());

        authed(get("/api/matches"), seeker)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].areas[0]").exists())
                .andExpect(jsonPath("$[0].score.rent").value(100));
    }

    @Test
    void studentsOfTheSameFacultyAreToldSo() throws Exception {
        Map<String, Object> me = apartment("SEARCH_TOGETHER", "KRIVA_PALANKA");
        String token = user(me);
        Map<String, Object> classmate = apartment("SEARCH_TOGETHER", "KRIVA_PALANKA");
        classmate.put("faculty", "Факултет за информатички науки и компјутерско инженерство (ФИНКИ), Скопје");
        user(classmate);

        authed(get("/api/matches"), token).andExpect(jsonPath("$[0].score.sameFaculty").value(true));
    }

    private void assertDormScore(JsonNode dorm, int expected, boolean differs) throws Exception {
        Map<String, Object> a = dormProfile(dorm.get("id").asLong(), dorm.get("roomOptions").get(0).get("id").asLong());
        Map<String, Object> b = dormProfile(dorm.get("id").asLong(), dorm.get("roomOptions").get(1).get("id").asLong());
        // Different faculties, so only the room type changes the score.
        b.put("faculty", "ФЕИТ");
        String token = user(a);
        user(b);

        authed(get("/api/matches"), token)
                .andExpect(jsonPath("$[0].score.total").value(expected))
                .andExpect(jsonPath("$[0].score.differentRoomType").value(differs))
                .andExpect(jsonPath("$[0].score.rent").isEmpty());
    }

    private JsonNode dorms() throws Exception {
        return json.readTree(authed(get("/api/reference"), user(apartment("SEARCH_TOGETHER", "PEHCEVO")))
                .andReturn().getResponse().getContentAsString()).get("dorms");
    }

    private long id(String token) throws Exception {
        return json.readTree(authed(get("/api/me"), token).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private static int indexOf(JsonNode matches, String firstName) {
        for (int i = 0; i < matches.size(); i++) {
            if (firstName.equals(matches.get(i).get("profile").get("firstName").asText())) {
                return i;
            }
        }
        throw new AssertionError(firstName + " is not among the matches");
    }

    private String register() throws Exception {
        return TestAccounts.register(mvc, mail);
    }

    private String user(Map<String, Object> profile) throws Exception {
        String token = register();
        send(put("/api/profile/me"), token, profile).andExpect(status().isOk());
        return token;
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Map<String, ?> body)
            throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private static Map<String, Object> listing(String area) {
        Map<String, Object> listing = new HashMap<>();
        listing.put("propertyType", "APARTMENT");
        listing.put("address", "Главна 1");
        listing.put("area", area);
        listing.put("latitude", 41.7);
        listing.put("longitude", 22.8);
        listing.put("monthlyRent", 150);
        listing.put("freeSpots", 1);
        listing.put("description", "Стан во центар.");
        return listing;
    }

    private static Map<String, Object> dormProfile(long dormId, long optionId) {
        Map<String, Object> profile = base();
        profile.put("housingType", "DORM");
        profile.put("dormId", dormId);
        profile.put("dormRoomOptionId", optionId);
        return profile;
    }

    private static Map<String, Object> apartment(String situation, String area) {
        Map<String, Object> profile = base();
        profile.put("housingType", "APARTMENT");
        profile.put("situation", situation);
        profile.put("budgetMin", 100);
        profile.put("budgetMax", 200);
        profile.put("areas", List.of(area));
        profile.put("propertyTypes", List.of("APARTMENT", "HOUSE"));
        return profile;
    }

    private static Map<String, Object> base() {
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", "Ана");
        profile.put("gender", "FEMALE");
        profile.put("birthDate", "2003-05-14");
        profile.put("faculty", "ФИНКИ");
        profile.put("bio", "");
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
        profile.put("acceptsPets", false);
        profile.put("roommateGenderPreference", "SAME");
        profile.put("hobbyIds", List.of(1, 2));
        return profile;
    }
}
