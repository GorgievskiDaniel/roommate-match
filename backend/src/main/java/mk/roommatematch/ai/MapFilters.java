package mk.roommatematch.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.reference.Area;

/** What a search asks for. Built from the model's answer, trusting none of its values. */
record MapFilters(
        boolean understood,
        Set<PlaceKind> kinds,
        boolean wholeOfSkopje,
        Set<Area> areas,
        Set<String> dorms,
        Integer minRent,
        Integer maxRent,
        boolean noSmokers,
        boolean noPets,
        Gender residentGender) {

    enum PlaceKind { APARTMENT, HOUSE, DORM }

    static final String SKOPJE_CODE = "SKOPJE";

    static MapFilters from(JsonNode answer, Set<String> knownDorms) {
        Set<PlaceKind> kinds = EnumSet.noneOf(PlaceKind.class);
        forEachText(answer.path("placeKinds"), value -> parse(PlaceKind.class, value, kinds));
        Set<Area> areas = EnumSet.noneOf(Area.class);
        boolean[] wholeOfSkopje = {false};
        forEachText(answer.path("areas"), value -> {
            if (SKOPJE_CODE.equals(value)) {
                wholeOfSkopje[0] = true;
            } else {
                parse(Area.class, value, areas);
            }
        });
        Set<String> dorms = new LinkedHashSet<>();
        forEachText(answer.path("dorms"), value -> {
            if (knownDorms.contains(value)) {
                dorms.add(value);
            }
        });
        return new MapFilters(
                answer.path("understood").asBoolean(false),
                kinds,
                wholeOfSkopje[0],
                areas,
                dorms,
                rent(answer.path("minRent")),
                rent(answer.path("maxRent")),
                answer.path("noSmokers").asBoolean(false),
                answer.path("noPets").asBoolean(false),
                gender(answer.path("residentGender").asText("")));
    }

    boolean hasLocation() {
        return wholeOfSkopje || !areas.isEmpty();
    }

    private static void forEachText(JsonNode array, Consumer<String> action) {
        array.forEach(item -> action.accept(item.asText()));
    }

    private static <E extends Enum<E>> void parse(Class<E> type, String value, Set<E> target) {
        try {
            target.add(Enum.valueOf(type, value));
        } catch (IllegalArgumentException ignored) {
            // A value outside the schema is dropped, which only makes the search wider.
        }
    }

    private static Integer rent(JsonNode value) {
        return value.isIntegralNumber() && value.asInt() >= 0 ? value.asInt() : null;
    }

    private static Gender gender(String value) {
        return switch (value) {
            case "FEMALE" -> Gender.FEMALE;
            case "MALE" -> Gender.MALE;
            default -> null;
        };
    }
}
