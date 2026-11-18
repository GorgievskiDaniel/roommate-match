package mk.roommatematch.distance;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Asks an OpenStreetMap routing server for the length of a route. The public servers are shared,
 * so a route already asked for is kept instead of being requested again.
 */
@Component
public class RouteClient {

    public enum Mode {
        WALKING("routed-foot"), DRIVING("routed-car");

        private final String profile;

        Mode(String profile) {
            this.profile = profile;
        }
    }

    public record Route(double meters, double seconds) {
    }

    private static final int CACHE_SIZE = 5000;

    private final RestClient restClient;
    private final String baseUrl;
    private final Map<String, Route> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Route> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    @Autowired
    public RouteClient(RestClient.Builder builder,
                       @Value("${app.routing.base-url}") String baseUrl,
                       @Value("${app.routing.timeout}") Duration timeout) {
        this.restClient = builder.clone()
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS.withReadTimeout(timeout)))
                .defaultHeader("User-Agent", "roommate-match-thesis/0.1")
                .build();
        this.baseUrl = baseUrl;
    }

    RouteClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    public Route route(Mode mode, double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        // Longitude comes first in this API, and the word "driving" in the path is fixed for every profile.
        String coordinates = String.format(Locale.ROOT, "%.5f,%.5f;%.5f,%.5f",
                fromLongitude, fromLatitude, toLongitude, toLatitude);
        String key = mode + ":" + coordinates;
        Route cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Route route = fetch(mode, coordinates);
        cache.put(key, route);
        return route;
    }

    private Route fetch(Mode mode, String coordinates) {
        try {
            JsonNode response = restClient.get()
                    .uri(baseUrl + "/" + mode.profile + "/route/v1/driving/" + coordinates + "?overview=false")
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode route = response == null ? null : response.path("routes").path(0);
            if (route == null || !route.has("distance") || !route.has("duration")) {
                throw unavailable();
            }
            return new Route(route.get("distance").asDouble(), route.get("duration").asDouble());
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Растојанието моментално не може да се пресмета. Обидете се повторно подоцна.");
    }
}
