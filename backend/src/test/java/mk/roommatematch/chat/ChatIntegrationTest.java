package mk.roommatematch.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
class ChatIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private record User(String token, long id) {
    }

    @Test
    void pairHasOneConversationWhoeverStartsIt() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");

        long fromAna = start(ana, vera);
        long fromVera = start(vera, ana);

        assertThat(fromAna).isEqualTo(fromVera);
        send(post("/api/conversations/" + fromAna), ana, null).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void conversationWithoutMessagesStaysOutOfTheList() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");
        long id = start(ana, vera);

        authed(get("/api/conversations"), vera).andExpect(jsonPath("$.length()").value(0));

        message(id, ana, "Здраво");

        authed(get("/api/conversations"), vera)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].otherName").value("Ана"))
                .andExpect(jsonPath("$[0].lastMessage").value("Здраво"));
    }

    @Test
    void unreadCountsOnlyTheOtherSideUntilRead() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");
        long id = start(ana, vera);
        message(id, ana, "Прва");
        message(id, ana, "Втора");

        authed(get("/api/conversations/unread"), vera).andExpect(jsonPath("$.unread").value(2));
        authed(get("/api/conversations/unread"), ana).andExpect(jsonPath("$.unread").value(0));

        authed(post("/api/conversations/" + id + "/read"), vera).andExpect(status().isNoContent());

        authed(get("/api/conversations/unread"), vera).andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void outsiderCannotReadOrWriteAConversation() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");
        User outsider = user("Друга");
        long id = start(ana, vera);
        message(id, ana, "Приватно");

        authed(get("/api/conversations/" + id), outsider).andExpect(status().isNotFound());
        authed(get("/api/conversations/" + id + "/messages"), outsider).andExpect(status().isNotFound());
        authed(post("/api/conversations/" + id + "/read"), outsider).andExpect(status().isNotFound());
        send(post("/api/conversations/" + id + "/messages"), outsider, Map.of("body", "Упад"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");
        long id = start(ana, vera);

        send(post("/api/conversations"), ana, Map.of("userId", ana.id())).andExpect(status().isBadRequest());
        send(post("/api/conversations"), ana, Map.of("userId", 999_999)).andExpect(status().isNotFound());
        send(post("/api/conversations/" + id + "/messages"), ana, Map.of("body", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasKey("body")));
        send(post("/api/conversations/" + id + "/messages"), ana, Map.of("body", "ш".repeat(2001)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userWithoutProfileCannotStartAConversation() throws Exception {
        String noProfile = register();
        User vera = user("Вера");

        send(post("/api/conversations"), new User(noProfile, 0), Map.of("userId", vera.id()))
                .andExpect(status().isForbidden());
    }

    @Test
    void messagesPageOldestFirstAndCanLoadOlder() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");
        long id = start(ana, vera);
        for (int i = 1; i <= 52; i++) {
            message(id, ana, "Порака " + i);
        }

        JsonNode latest = json.readTree(authed(get("/api/conversations/" + id + "/messages"), vera)
                .andReturn().getResponse().getContentAsString());
        assertThat(latest).hasSize(50);
        assertThat(latest.get(0).get("body").asText()).isEqualTo("Порака 3");
        assertThat(latest.get(49).get("body").asText()).isEqualTo("Порака 52");

        authed(get("/api/conversations/" + id + "/messages?before=" + latest.get(0).get("id").asLong()), vera)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].body").value("Порака 1"));
    }

    @Test
    void blockStopsMessagesAndHidesTheConversationFromBothUntilLifted() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");
        long id = start(ana, vera);
        message(id, ana, "Здраво");

        authed(post("/api/blocks/" + ana.id()), vera).andExpect(status().isNoContent());

        send(post("/api/conversations/" + id + "/messages"), ana, Map.of("body", "Пак јас"))
                .andExpect(status().isForbidden());
        send(post("/api/conversations/" + id + "/messages"), vera, Map.of("body", "И јас"))
                .andExpect(status().isForbidden());
        send(post("/api/conversations"), ana, Map.of("userId", vera.id())).andExpect(status().isForbidden());
        authed(get("/api/conversations"), ana).andExpect(jsonPath("$.length()").value(0));
        authed(get("/api/conversations"), vera).andExpect(jsonPath("$.length()").value(0));
        authed(get("/api/conversations/" + id + "/messages"), ana).andExpect(status().isNotFound());
        authed(get("/api/blocks"), vera).andExpect(jsonPath("$[0].firstName").value("Ана"));
        authed(get("/api/blocks"), ana).andExpect(jsonPath("$.length()").value(0));

        // Only the one who blocked can lift it.
        authed(delete("/api/blocks/" + vera.id()), ana).andExpect(status().isNoContent());
        authed(get("/api/conversations"), vera).andExpect(jsonPath("$.length()").value(0));

        authed(delete("/api/blocks/" + ana.id()), vera).andExpect(status().isNoContent());
        authed(get("/api/conversations"), vera).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void newMessageReachesTheRecipientOverTheSocket() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");
        long id = start(ana, vera);
        StompSession session = connect(vera.token());
        BlockingQueue<Map<String, Object>> received = subscribe(session, "/user/queue/messages");
        // Lets the broker register the subscription before anything is sent.
        Thread.sleep(300);

        message(id, ana, "Во живо");

        Map<String, Object> delivered = received.poll(5, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(delivered.get("body")).isEqualTo("Во живо");
        assertThat(((Number) delivered.get("conversationId")).longValue()).isEqualTo(id);
        session.disconnect();
    }

    @Test
    void otherPeoplesMessagesAreNotDelivered() throws Exception {
        User ana = user("Ана");
        User vera = user("Вера");
        User outsider = user("Друга");
        long id = start(ana, vera);
        StompSession session = connect(outsider.token());
        BlockingQueue<Map<String, Object>> received = subscribe(session, "/user/queue/messages");
        Thread.sleep(300);

        message(id, ana, "Приватно");

        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
        session.disconnect();
    }

    @Test
    void socketRejectsAConnectionWithABadToken() {
        assertThatThrownBy(() -> connect("not-a-token")).isInstanceOf(Exception.class);
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        return received;
    }

    private long start(User from, User to) throws Exception {
        String body = send(post("/api/conversations"), from, Map.of("userId", to.id()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void message(long conversationId, User from, String body) throws Exception {
        send(post("/api/conversations/" + conversationId + "/messages"), from, Map.of("body", body))
                .andExpect(status().isCreated());
    }

    private String register() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", UUID.randomUUID() + "@students.test", "password", "lozinka123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private User user(String firstName) throws Exception {
        String token = register();
        mvc.perform(put("/api/profile/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile(firstName))))
                .andExpect(status().isOk());
        String me = mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return new User(token, json.readTree(me).get("id").asLong());
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, User user) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + user.token()));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, User user, Map<String, ?> body) throws Exception {
        request.header("Authorization", "Bearer " + user.token());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private static Map<String, Object> profile(String firstName) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", firstName);
        profile.put("gender", "FEMALE");
        profile.put("birthDate", "2003-05-14");
        profile.put("faculty", "ФИНКИ");
        profile.put("bio", "");
        profile.put("housingType", "APARTMENT");
        profile.put("situation", "SEARCH_TOGETHER");
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
        return profile;
    }
}
