package mk.roommatematch.matching;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileEnums.GenderPreference;
import mk.roommatematch.profile.ProfileEnums.GuestsFrequency;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.SleepSchedule;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.reference.Area;
import org.springframework.stereotype.Component;

/**
 * Decides whether two students can live together and how well they fit. It works in two steps:
 * rules that must hold for both sides, then a weighted score of what remains. The result depends
 * only on the two profiles, so the same pair always gets the same score, in either order.
 */
@Component
public class MatchScorer {

    /** Municipalities of Skopje this far apart or more get the lowest location score. */
    private static final double SKOPJE_FAR_KM = 15;
    private static final double SKOPJE_NEAR_SCORE = 0.8;
    private static final double SKOPJE_FAR_SCORE = 0.3;
    private static final double EARTH_RADIUS_KM = 6371;
    private static final double DIFFERENT_LIFESTYLE_SCORE = 0.5;

    public record ListedPlace(PropertyType type, Area area, int rent) {
    }

    public record Person(
            long userId,
            Gender gender,
            HousingType housingType,
            Situation situation,
            Long dormId,
            Long roomOptionId,
            boolean roomTypeAffectsMatch,
            Integer budgetMin,
            Integer budgetMax,
            Set<Area> areas,
            Set<PropertyType> propertyTypes,
            boolean smoker,
            boolean hasPets,
            GuestsFrequency guests,
            int tidiness,
            SleepSchedule sleep,
            int quietNeed,
            boolean acceptsSmoker,
            boolean acceptsPets,
            GenderPreference genderPreference,
            Set<Long> hobbies,
            /** The place this person already lives in, when it has a listing. */
            ListedPlace place) {
    }

    /** Parts are percentages. Rent and location are null for dorms, where they do not count. */
    public record Score(int total, int habits, int hobbies, Integer rent, Integer location, boolean differentRoomType) {
    }

    private final MatchingProperties properties;

    public MatchScorer(MatchingProperties properties) {
        this.properties = properties;
    }

    public Optional<Score> score(Person a, Person b) {
        if (a.userId() == b.userId() || a.housingType() != b.housingType() || !acceptEachOther(a, b)) {
            return Optional.empty();
        }
        return a.housingType() == HousingType.DORM ? scoreDorm(a, b) : scoreApartment(a, b);
    }

    private Optional<Score> scoreDorm(Person a, Person b) {
        if (a.dormId() == null || !a.dormId().equals(b.dormId()) || a.gender() != b.gender()) {
            return Optional.empty();
        }
        double habits = habits(a, b);
        double hobbies = hobbies(a, b);
        double total = properties.dorm().habits() * habits + properties.dorm().hobbies() * hobbies;
        boolean differentRoomType = a.roomTypeAffectsMatch() && !sameRoomType(a, b);
        if (differentRoomType) {
            total *= properties.differentRoomTypeFactor();
        }
        return Optional.of(new Score(percent(total), percent(habits), percent(hobbies), null, null, differentRoomType));
    }

    private Optional<Score> scoreApartment(Person a, Person b) {
        if (!situationsFit(a.situation(), b.situation()) || !sharePropertyType(a, b)) {
            return Optional.empty();
        }
        Optional<Double> location = location(a, b);
        if (location.isEmpty()) {
            return Optional.empty();
        }
        double habits = habits(a, b);
        double hobbies = hobbies(a, b);
        double rent = rent(a, b);
        var weights = properties.apartment();
        double total = weights.habits() * habits + weights.rent() * rent
                + weights.location() * location.get() + weights.hobbies() * hobbies;
        return Optional.of(new Score(percent(total), percent(habits), percent(hobbies),
                percent(rent), percent(location.get()), false));
    }

    private static boolean acceptEachOther(Person a, Person b) {
        return accepts(a, b) && accepts(b, a);
    }

    private static boolean accepts(Person who, Person other) {
        return (who.acceptsSmoker() || !other.smoker())
                && (who.acceptsPets() || !other.hasPets())
                && (who.genderPreference() == GenderPreference.ANY || who.gender() == other.gender());
    }

    /** Someone with a place needs someone without one, and people searching together need each other. */
    private static boolean situationsFit(Situation a, Situation b) {
        if (a == null || b == null) {
            return false;
        }
        return switch (a) {
            case HAS_PLACE -> b == Situation.NEEDS_PLACE;
            case NEEDS_PLACE -> b == Situation.HAS_PLACE;
            case SEARCH_TOGETHER -> b == Situation.SEARCH_TOGETHER;
        };
    }

    private static boolean sharePropertyType(Person a, Person b) {
        Set<PropertyType> shared = new HashSet<>(propertyTypes(a));
        shared.retainAll(propertyTypes(b));
        return !shared.isEmpty();
    }

    private static Set<PropertyType> propertyTypes(Person person) {
        return person.place() != null ? Set.of(person.place().type()) : person.propertyTypes();
    }

    private static Set<Area> areas(Person person) {
        return person.place() != null ? Set.of(person.place().area()) : person.areas();
    }

    /**
     * A shared area scores full marks. Without one, only two municipalities of Skopje can still
     * match, for less the farther apart they are. Different towns do not match at all.
     */
    private static Optional<Double> location(Person a, Person b) {
        double best = -1;
        for (Area first : areas(a)) {
            for (Area second : areas(b)) {
                if (first == second) {
                    return Optional.of(1.0);
                }
                if (first.isSkopje() && second.isSkopje()) {
                    best = Math.max(best, skopjeScore(distanceKm(first, second)));
                }
            }
        }
        return best < 0 ? Optional.empty() : Optional.of(best);
    }

    private static double skopjeScore(double distanceKm) {
        double closeness = 1 - Math.min(distanceKm, SKOPJE_FAR_KM) / SKOPJE_FAR_KM;
        return SKOPJE_FAR_SCORE + (SKOPJE_NEAR_SCORE - SKOPJE_FAR_SCORE) * closeness;
    }

    private static double distanceKm(Area a, Area b) {
        double latitudeDelta = Math.toRadians(b.getLatitude() - a.getLatitude());
        double longitudeDelta = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(a.getLatitude())) * Math.cos(Math.toRadians(b.getLatitude()))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }

    /** Overlapping budgets score full marks. Otherwise the score falls with the gap between them. */
    private static double rent(Person a, Person b) {
        int aMin = rentMin(a);
        int aMax = rentMax(a);
        int bMin = rentMin(b);
        int bMax = rentMax(b);
        int gap = Math.max(aMin, bMin) - Math.min(aMax, bMax);
        if (gap <= 0) {
            return 1;
        }
        return Math.max(0, 1 - (double) gap / Math.max(aMax, bMax));
    }

    private static int rentMin(Person person) {
        return person.place() != null ? person.place().rent() : person.budgetMin();
    }

    private static int rentMax(Person person) {
        return person.place() != null ? person.place().rent() : person.budgetMax();
    }

    private static double habits(Person a, Person b) {
        return (scale(a.guests().ordinal(), b.guests().ordinal(), GuestsFrequency.values().length - 1)
                + scale(a.tidiness(), b.tidiness(), 4)
                + scale(a.sleep().ordinal(), b.sleep().ordinal(), SleepSchedule.values().length - 1)
                + scale(a.quietNeed(), b.quietNeed(), 4)
                + (a.smoker() == b.smoker() ? 1 : DIFFERENT_LIFESTYLE_SCORE)
                + (a.hasPets() == b.hasPets() ? 1 : DIFFERENT_LIFESTYLE_SCORE)) / 6;
    }

    private static double scale(int a, int b, int range) {
        return 1 - (double) Math.abs(a - b) / range;
    }

    /** Jaccard similarity: shared hobbies out of all hobbies the two have between them. */
    private static double hobbies(Person a, Person b) {
        Set<Long> union = new HashSet<>(a.hobbies());
        union.addAll(b.hobbies());
        if (union.isEmpty()) {
            return 0;
        }
        Set<Long> shared = new HashSet<>(a.hobbies());
        shared.retainAll(b.hobbies());
        return (double) shared.size() / union.size();
    }

    private static boolean sameRoomType(Person a, Person b) {
        return a.roomOptionId() != null && a.roomOptionId().equals(b.roomOptionId());
    }

    private static int percent(double value) {
        return (int) Math.round(value * 100);
    }
}
