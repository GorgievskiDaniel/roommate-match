package mk.roommatematch.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import mk.roommatematch.distance.AddressSearch.Suggestion;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AddressSearchTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AddressSearch search = new AddressSearch(builder.build(), "https://photon.test");

    private static final String ANSWER = """
            {"features":[
              {"geometry":{"coordinates":[21.4097,42.0041]},
               "properties":{"countrycode":"MK","street":"Руѓер Бошковиќ","housenumber":"16","district":"Карпош 4","city":"Скопје"}},
              {"geometry":{"coordinates":[21.4097,42.0041]},
               "properties":{"countrycode":"MK","street":"Руѓер Бошковиќ","housenumber":"16","district":"Карпош 4","city":"Скопје"}},
              {"geometry":{"coordinates":[21.73,42.14]},
               "properties":{"countrycode":"MK","name":"Руѓер Бошковиќ","city":"Куманово","county":"Општина Куманово"}},
              {"geometry":{"coordinates":[22.0,43.0]},
               "properties":{"countrycode":"RS","name":"Somewhere else"}}
            ]}""";

    @Test
    void suggestsMacedonianAddressesOnceEachWithTheirPoint() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith("https://photon.test/api/?q=")))
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        assertThat(search.search("Руѓер Бошковиќ 16")).containsExactly(
                new Suggestion("Руѓер Бошковиќ 16, Карпош 4, Скопје", 42.0041, 21.4097),
                new Suggestion("Руѓер Бошковиќ, Куманово", 42.14, 21.73));
        // The same search again is answered from memory.
        assertThat(search.search("  руѓер   бошковиќ 16 ")).hasSize(2);
        server.verify();
    }

    @Test
    void shortTextIsNotSearched() {
        assertThat(search.search("ру")).isEmpty();
        server.verify();
    }

    @Test
    void anUnavailableServiceGivesNoSuggestions() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://photon.test/api/")))
                .andRespond(withServerError());

        assertThat(search.search("Партизански одреди")).isEmpty();
    }
}
