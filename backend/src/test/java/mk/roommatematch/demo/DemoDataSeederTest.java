package mk.roommatematch.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = "app.uploads.dir=target/test-uploads")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles({"dev", "demo"})
class DemoDataSeederTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DemoDataSeeder seeder;

    private String login(String key) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "demo-" + key + "@students.test", "password", DemoDataSeeder.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    @Test
    void demoUsersCanLogInAndHaveCompleteProfiles() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + login("ana")))
                .andExpect(jsonPath("$.profileCompleted").value(true));
    }

    @Test
    void mapShowsPhotographedListingsAndDormSeekers() throws Exception {
        String body = mvc.perform(get("/api/map").header("Authorization", "Bearer " + login("iva")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings.length()").value(14))
                .andReturn().getResponse().getContentAsString();

        JsonNode goceDelcev = json.readTree(body).get("dorms").get(0);
        assertThat(goceDelcev.get("seekers").asInt()).isEqualTo(5);
    }

    @Test
    void everyListingShowsItsOwnPhotos() throws Exception {
        String body = mvc.perform(get("/api/listings/mine").header("Authorization", "Bearer " + login("ana")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode images = json.readTree(body).get("images");

        assertThat(images.size()).isEqualTo(7);
        images.forEach(image -> assertThat(image.get("url").asText()).endsWith(".jpg"));
        assertThat(jdbc.queryForObject("select count(*) from listing_images", Long.class)).isEqualTo(88L);
    }

    @Test
    void residentJoinedTheFirstListing() throws Exception {
        mvc.perform(get("/api/listings/mine").header("Authorization", "Bearer " + login("elena")))
                .andExpect(jsonPath("$.residents.length()").value(2));
    }

    @Test
    void everyKindOfStudentHasMatches() throws Exception {
        for (String key : new String[] {"iva", "ana", "sara", "david", "goran", "kristina", "arta", "jana", "lea", "hana"}) {
            mvc.perform(get("/api/matches").header("Authorization", "Bearer " + login(key)))
                    .andExpect(jsonPath("$.length()", greaterThan(0)));
        }
    }

    @Test
    void runningAgainAddsNothing() throws Exception {
        Long before = jdbc.queryForObject("select count(*) from users", Long.class);

        seeder.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(before);
    }
}
