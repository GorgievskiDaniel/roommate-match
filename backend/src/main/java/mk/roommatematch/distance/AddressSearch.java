package mk.roommatematch.distance;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Suggests addresses while someone types, from Photon, a search built on OpenStreetMap that is meant
 * for search as you type. Only places in North Macedonia are asked for, and answers are kept, since
 * the public server is shared.
 */
@Component
public class AddressSearch {

    public record Suggestion(String label, double latitude, double longitude) {
    }

    /** West, south, east and north edges of North Macedonia. */
    private static final String MACEDONIA = "20.4,40.8,23.1,42.4";
    private static final int RESULTS = 6;
    private static final int CACHE_SIZE = 2000;

    private final RestClient restClient;
    private final String baseUrl;
    private final Map<String, List<Suggestion>> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Suggestion>> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    @Autowired
    public AddressSearch(RestClient.Builder builder,
                         @Value("${app.geocoding.base-url}") String baseUrl,
                         @Value("${app.geocoding.timeout}") Duration timeout) {
        this(builder.clone()
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS.withReadTimeout(timeout)))
                .defaultHeader("User-Agent", "roommate-match-thesis/0.1")
                .build(), baseUrl);
    }

    AddressSearch(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    /** An empty list when nothing is found or the search service does not answer; typing carries on either way. */
    public List<Suggestion> search(String query) {
        String text = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (text.length() < 3) {
            return List.of();
        }
        String key = text.toLowerCase(Locale.ROOT);
        List<Suggestion> known = cache.get(key);
        if (known != null) {
            return known;
        }
        try {
            JsonNode answer = restClient.get()
                    .uri(baseUrl + "/api/?q={q}&limit={limit}&bbox={bbox}", text, RESULTS + 4, MACEDONIA)
                    .retrieve()
                    .body(JsonNode.class);
            List<Suggestion> found = suggestions(answer);
            cache.put(key, found);
            return found;
        } catch (RestClientException e) {
            return List.of();
        }
    }

    private static List<Suggestion> suggestions(JsonNode answer) {
        List<Suggestion> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (answer == null) {
            return result;
        }
        for (JsonNode feature : answer.path("features")) {
            JsonNode point = feature.path("geometry").path("coordinates");
            JsonNode p = feature.path("properties");
            if (point.size() < 2 || !"MK".equalsIgnoreCase(p.path("countrycode").asText("MK"))) {
                continue;
            }
            String label = label(p);
            if (!label.isBlank() && seen.add(label) && result.size() < RESULTS) {
                result.add(new Suggestion(label, point.get(1).asDouble(), point.get(0).asDouble()));
            }
        }
        return result;
    }

    /** For example "Руѓер Бошковиќ 16, Карпош 4, Скопје". */
    private static String label(JsonNode p) {
        String street = p.path("street").asText("");
        String number = p.path("housenumber").asText("");
        String name = p.path("name").asText("");
        String first = !street.isBlank()
                ? (name.isBlank() || name.equals(street) ? "" : name + ", ") + street + (number.isBlank() ? "" : " " + number)
                : name + (number.isBlank() ? "" : " " + number);
        Set<String> parts = new LinkedHashSet<>();
        if (!first.isBlank()) {
            parts.add(first.trim());
        }
        for (String field : new String[] {"district", "city"}) {
            String value = p.path(field).asText("").trim();
            if (!value.isBlank() && !value.startsWith("Општина")) {
                parts.add(value);
            }
        }
        return String.join(", ", parts);
    }
}
