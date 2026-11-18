package mk.roommatematch;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class ListingIntegrationTest {

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
    void anyoneWithAProfileCanCreateAListing() throws Exception {
        String seeker = userWithProfile(profile("NEEDS_PLACE"));
        send(post("/api/listings"), seeker, listing()).andExpect(status().isCreated());

        Map<String, Object> dormProfile = profile("NEEDS_PLACE");
        dormProfile.put("housingType", "DORM");
        dormProfile.put("dormId", 4);
        dormProfile.put("situation", null);
        dormProfile.put("budgetMin", null);
        dormProfile.put("budgetMax", null);
        dormProfile.put("areas", List.of());
        dormProfile.put("propertyTypes", List.of());
        send(post("/api/listings"), userWithProfile(dormProfile), listing()).andExpect(status().isCreated());

        String noProfile = TestAccounts.register(mvc, mail);
        send(post("/api/listings"), noProfile, listing()).andExpect(status().isForbidden());
    }

    @Test
    void listingSaysWhichGenderTheOwnerWantsARoommateOf() throws Exception {
        String onlyWomen = userWithProfile(profile("HAS_PLACE"));
        Map<String, Object> anyone = profile("HAS_PLACE");
        anyone.put("roommateGenderPreference", "ANY");
        String doesNotMind = userWithProfile(anyone);
        String viewer = userWithProfile(profile("NEEDS_PLACE"));
        long first = createListing(onlyWomen);
        long second = createListing(doesNotMind);
        uploadPng(first, onlyWomen).andExpect(status().isOk());
        uploadPng(second, doesNotMind).andExpect(status().isOk());

        authed(get("/api/listings/" + first), viewer).andExpect(jsonPath("$.roommateGender").value("FEMALE"));
        authed(get("/api/listings/" + second), viewer).andExpect(jsonPath("$.roommateGender").value(nullValue()));
    }

    @Test
    void listingAppearsOnTheMapOnlyAfterAPhotoIsAdded() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        String viewer = userWithProfile(profile("NEEDS_PLACE"));
        long id = createListing(owner);
        String path = "$.listings[*].id";

        authed(get("/api/map"), viewer).andExpect(jsonPath(path, not(hasItem((int) id))));
        authed(get("/api/listings/" + id), viewer).andExpect(status().isNotFound());

        uploadPng(id, owner).andExpect(status().isOk());

        authed(get("/api/map"), viewer).andExpect(jsonPath(path, hasItem((int) id)));
        authed(get("/api/listings/" + id), viewer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Партизанска 1"))
                .andExpect(jsonPath("$.inviteCode").value(nullValue()))
                .andExpect(jsonPath("$.residents[0].firstName").value("Ана"))
                .andExpect(jsonPath("$.residents[0].email").doesNotExist())
                .andExpect(jsonPath("$.residents[0].birthDate").doesNotExist());
    }

    @Test
    void browsePageShowsWhatTheListingOffers() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        String viewer = userWithProfile(profile("NEEDS_PLACE"));
        Map<String, Object> offers = listing();
        offers.put("amenities", List.of("WIFI", "PARKING", "BALCONY"));
        String created = send(post("/api/listings"), owner, offers)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amenities[*].code", contains("PARKING", "BALCONY", "WIFI")))
                .andExpect(jsonPath("$.amenities[0].label").value("Паркинг"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        String card = "$.listings[?(@.id == " + id + ")]";

        authed(get("/api/browse"), viewer).andExpect(jsonPath(card).isEmpty());

        uploadPng(id, owner).andExpect(status().isOk());

        authed(get("/api/browse"), viewer)
                .andExpect(status().isOk())
                .andExpect(jsonPath(card + ".amenities[*].label", hasItem("Тераса")))
                .andExpect(jsonPath(card + ".hobbies").doesNotExist())
                .andExpect(jsonPath(card + ".area").value(hasItem("KARPOSH")))
                .andExpect(jsonPath(card + ".imageCount").value(hasItem(1)))
                .andExpect(jsonPath(card + ".inviteCode").doesNotExist())
                .andExpect(jsonPath("$.dorms").isNotEmpty());
    }

    @Test
    void listingWithoutAmenitiesHasNone() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));

        send(post("/api/listings"), owner, listing())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amenities").isEmpty());
    }

    @Test
    void aFlatCannotHaveAYard() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        Map<String, Object> flat = listing();
        flat.put("amenities", List.of("YARD"));

        send(post("/api/listings"), owner, flat)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amenities").value("Двор не одговара за стан."));
    }

    @Test
    void dormPagesHaveTheirPhotosAndTheNewDormSaysWhenItOpens() throws Exception {
        String viewer = userWithProfile(profile("NEEDS_PLACE"));
        String body = authed(get("/api/browse"), viewer).andReturn().getResponse().getContentAsString();
        long kuzman = 0;
        for (var dorm : json.readTree(body).get("dorms")) {
            if (dorm.get("name").asText().equals("ДСД Кузман Јосифовски")) {
                kuzman = dorm.get("id").asLong();
            }
        }

        authed(get("/api/dorms/" + kuzman), viewer)
                .andExpect(jsonPath("$.openingNote").value("Ќе биде пуштен во употреба во јануари 2027 година."))
                .andExpect(jsonPath("$.photoUrls.length()").value(11));
        authed(get("/api/dorms/1"), viewer)
                .andExpect(jsonPath("$.openingNote").isEmpty())
                .andExpect(jsonPath("$.photoUrls[0]").value(org.hamcrest.Matchers.matchesPattern("/api/dorm-photos/goce-delchev/1\\.jpg\\?v=[0-9a-f]+")));
        mvc.perform(get("/api/dorm-photos/goce-delchev/1.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
        mvc.perform(get("/api/dorm-photos/..%2Fapplication/1.jpg")).andExpect(status().is4xxClientError());
    }

    @Test
    void uploadedImageIsServedWithoutAToken() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        long id = createListing(owner);

        String body = uploadPng(id, owner).andReturn().getResponse().getContentAsString();
        String url = json.readTree(body).get("images").get(0).get("url").asText();

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void fileThatIsNotAnImageIsRejectedWhateverItClaims() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        long id = createListing(owner);
        MockMultipartFile fake = new MockMultipartFile("images", "x.png", "image/png", "<script>".getBytes());

        authed(multipart("/api/listings/" + id + "/images").file(fake), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("images")));
    }

    @Test
    void imagePathCannotEscapeTheUploadFolder() throws Exception {
        mvc.perform(get("/api/images/..%2F..%2Fpom.xml")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/images/pom.xml")).andExpect(status().isNotFound());
    }

    @Test
    void lastPhotoCannotBeRemoved() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        long id = createListing(owner);
        String body = uploadPng(id, owner).andReturn().getResponse().getContentAsString();
        long imageId = json.readTree(body).get("images").get(0).get("id").asLong();

        authed(delete("/api/listings/" + id + "/images/" + imageId), owner).andExpect(status().isBadRequest());
    }

    @Test
    void residentJoinsWithCodeAndCanLeave() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        String resident = userWithProfile(profile("HAS_PLACE"));
        JsonNode created = json.readTree(createListingBody(owner));
        long id = created.get("id").asLong();
        String code = created.get("inviteCode").asText();

        send(post("/api/listings/join"), resident, Map.of("code", code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.residents.length()").value(2));
        send(post("/api/listings/join"), resident, Map.of("code", code)).andExpect(status().isConflict());

        authed(put("/api/listings/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(listing())), resident).andExpect(status().isForbidden());

        authed(post("/api/listings/" + id + "/leave"), resident).andExpect(status().isNoContent());
        authed(get("/api/listings/mine"), resident).andExpect(status().isNotFound());
        authed(post("/api/listings/" + id + "/leave"), owner).andExpect(status().isConflict());
    }

    @Test
    void wrongCodeIsRejected() throws Exception {
        String resident = userWithProfile(profile("HAS_PLACE"));

        send(post("/api/listings/join"), resident, Map.of("code", "NEPOSTOI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("code")));
    }

    @Test
    void theProfileCanChangeWhileTheListingStays() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        createListing(owner);

        send(put("/api/profile/me"), owner, profile("NEEDS_PLACE")).andExpect(status().isOk());
        authed(get("/api/listings/mine"), owner).andExpect(status().isOk());
    }

    @Test
    void deletingAListingFreesTheOwner() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        long id = createListing(owner);
        uploadPng(id, owner).andExpect(status().isOk());

        authed(delete("/api/listings/" + id), owner).andExpect(status().isNoContent());

        authed(get("/api/listings/mine"), owner).andExpect(status().isNotFound());
        send(put("/api/profile/me"), owner, profile("NEEDS_PLACE")).andExpect(status().isOk());
    }

    @Test
    void dormsWithRoomTypesRequireOneFromThatDorm() throws Exception {
        String token = userWithProfile(profile("NEEDS_PLACE"));
        JsonNode reference = json.readTree(authed(get("/api/reference"), token)
                .andReturn().getResponse().getContentAsString());
        JsonNode goceDelcev = reference.get("dorms").get(0);
        JsonNode stivNaumov = reference.get("dorms").get(1);
        Map<String, Object> dorm = profile("NEEDS_PLACE");
        dorm.put("housingType", "DORM");
        dorm.put("dormId", goceDelcev.get("id").asLong());

        send(put("/api/profile/me"), token, dorm)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("dormRoomOptionId")));

        dorm.put("dormRoomOptionId", stivNaumov.get("roomOptions").get(0).get("id").asLong());
        send(put("/api/profile/me"), token, dorm).andExpect(status().isBadRequest());

        long option = goceDelcev.get("roomOptions").get(1).get("id").asLong();
        dorm.put("dormRoomOptionId", option);
        send(put("/api/profile/me"), token, dorm)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dormRoomOptionId").value((int) option));

        authed(get("/api/dorms/" + goceDelcev.get("id").asLong()), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seekers[0].roomOption").value("Со тоалет (блокови В и Г)"))
                .andExpect(jsonPath("$.seekers[0].profile.hobbies").isNotEmpty());
    }

    @Test
    void everyDormHasAPointOnTheMap() throws Exception {
        String token = userWithProfile(profile("NEEDS_PLACE"));

        authed(get("/api/map"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dorms.length()").value(9))
                .andExpect(jsonPath("$.dorms[*].latitude", not(hasItem(nullValue()))))
                .andExpect(jsonPath("$.dorms[*].longitude", not(hasItem(nullValue()))))
                .andExpect(jsonPath("$.dorms[0].blocks.length()").value(4))
                .andExpect(jsonPath("$.dorms[0].blocks[3].name").value("Блок Г"))
                .andExpect(jsonPath("$.dorms[0].blocks[3].roomOption").value("Со тоалет (блокови В и Г)"))
                .andExpect(jsonPath("$.dorms[1].blocks").isEmpty());
    }

    @Test
    void theOwnerChoosesTheMainPhoto() throws Exception {
        String owner = userWithProfile(profile("HAS_PLACE"));
        String other = userWithProfile(profile("NEEDS_PLACE"));
        long id = createListing(owner);
        uploadPng(id, owner).andExpect(status().isOk());
        String body = uploadPng(id, owner).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode images = json.readTree(body).get("images");
        long first = images.get(0).get("id").asLong();
        long second = images.get(1).get("id").asLong();

        authed(put("/api/listings/" + id + "/images/" + second + "/cover"), other).andExpect(status().isForbidden());
        authed(put("/api/listings/" + id + "/images/" + second + "/cover"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].id").value(second))
                .andExpect(jsonPath("$.images[1].id").value(first));

        String card = "$.listings[?(@.id == " + id + ")]";
        authed(get("/api/browse"), other)
                .andExpect(jsonPath(card + ".coverImageUrl").value(hasItem(images.get(1).get("url").asText())));

        // A photo added later goes to the end, so the main photo stays first.
        uploadPng(id, owner).andExpect(jsonPath("$.images[0].id").value(second));
    }

    @Test
    void stivNaumovIsShownInAvtokomanda() throws Exception {
        String viewer = userWithProfile(profile("NEEDS_PLACE"));

        authed(get("/api/browse"), viewer)
                .andExpect(jsonPath("$.dorms[?(@.name == 'Стив Наумов')].neighbourhood").value(hasItem("Автокоманда")));
        authed(get("/api/dorms/2"), viewer).andExpect(jsonPath("$.neighbourhood").value("Автокоманда"));
    }

    private long createListing(String token) throws Exception {
        return json.readTree(createListingBody(token)).get("id").asLong();
    }

    private String createListingBody(String token) throws Exception {
        return send(post("/api/listings"), token, listing())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions uploadPng(long id, String token) throws Exception {
        MockMultipartFile image = new MockMultipartFile("images", "stan.png", "image/png", PNG);
        return authed(multipart("/api/listings/" + id + "/images").file(image), token);
    }

    private String userWithProfile(Map<String, Object> profile) throws Exception {
        String token = TestAccounts.register(mvc, mail);
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

    private static Map<String, Object> listing() {
        Map<String, Object> listing = new HashMap<>();
        listing.put("propertyType", "APARTMENT");
        listing.put("address", "Партизанска 1");
        listing.put("area", "KARPOSH");
        listing.put("latitude", 42.0);
        listing.put("longitude", 21.42);
        listing.put("monthlyRent", 150);
        listing.put("freeSpots", 1);
        listing.put("description", "Светол стан до ФИНКИ.");
        return listing;
    }

    private static Map<String, Object> profile(String situation) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", "Ана");
        profile.put("gender", "FEMALE");
        profile.put("birthDate", "2003-05-14");
        profile.put("faculty", "ФИНКИ");
        profile.put("bio", "");
        profile.put("housingType", "APARTMENT");
        profile.put("situation", situation);
        profile.put("budgetMin", 100);
        profile.put("budgetMax", 200);
        profile.put("areas", List.of("KARPOSH"));
        profile.put("propertyTypes", List.of("APARTMENT", "HOUSE"));
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
}
