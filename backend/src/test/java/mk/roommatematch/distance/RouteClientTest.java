package mk.roommatematch.distance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import mk.roommatematch.distance.RouteClient.Mode;
import mk.roommatematch.distance.RouteClient.Route;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class RouteClientTest {

    private static final String REPLY = "{\"code\":\"Ok\",\"routes\":[{\"distance\":2363.4,\"duration\":1897.1}]}";
    private static final String URL =
            "https://route.test/routed-foot/route/v1/driving/21.38957,41.99904;21.40974,42.00410?overview=false";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RouteClient client = new RouteClient(builder.build(), "https://route.test");

    private Route walk() {
        return client.route(Mode.WALKING, 41.99904, 21.38957, 42.00410, 21.40974);
    }

    @Test
    void sendsLongitudeFirstAndReadsTheRoute() {
        server.expect(requestTo(URL)).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThat(walk()).isEqualTo(new Route(2363.4, 1897.1));
        server.verify();
    }

    @Test
    void theSameRouteIsRequestedOnlyOnce() {
        server.expect(once(), requestTo(URL)).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        walk();
        walk();

        server.verify();
    }

    @Test
    void failureIsReportedAndNotRemembered() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::walk).isInstanceOf(ResponseStatusException.class);
        assertThat(walk().meters()).isEqualTo(2363.4);
    }

    @Test
    void replyWithoutARouteIsReported() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"code\":\"NoRoute\",\"routes\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(this::walk).isInstanceOf(ResponseStatusException.class);
    }
}
