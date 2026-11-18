package mk.roommatematch.matching;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.profile.ProfileEnums.CleaningStyle;
import mk.roommatematch.profile.ProfileEnums.CommunicationLevel;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileEnums.GenderPreference;
import mk.roommatematch.profile.ProfileEnums.GuestsFrequency;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.PartnerStays;
import mk.roommatematch.profile.ProfileEnums.PartyAttitude;
import mk.roommatematch.profile.ProfileEnums.SharingStyle;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.profile.ProfileEnums.SleepSchedule;
import mk.roommatematch.profile.ProfileEnums.SoundLevel;
import mk.roommatematch.reference.Area;
import org.springframework.stereotype.Component;

/**
 * Decides whether two students can live together and how well they fit. It works in two steps:
 * requirements that must hold for both sides, then a weighted score of what remains. The result
 * depends only on the two profiles, so the same pair always gets the same score, in either order.
 *
 * <p>Requirements: the same kind of housing; gender preference; smoking and pets each accepts; for a
 * flat, situations that fit, a shared property type, a shared place and a rent within reach; for a
 * dorm, the same dorm and gender.
 *
 * <p>Score categories: everyday habits, living together, rent (flats only), hobbies and age. The
 * total is their weighted average, so 80% means that, on average across what matters, the two are
 * 80% alike; students of the same faculty get a small bonus on top.
 */
@Component
public class MatchScorer {

    /** How far a rent may go over someone's budget, as a share of it, before the place is out of reach. */
    static final double RENT_TOLERANCE = 0.2;

    public record ListedPlace(PropertyType type, Area area, int rent) {
    }

    public record Person(
            long userId,
            Gender gender,
            int age,
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
            CleaningStyle cleaning,
            SleepSchedule sleep,
            SoundLevel sound,
            CommunicationLevel communication,
            SharingStyle sharing,
            PartyAttitude parties,
            PartnerStays partnerStays,
            boolean acceptsSmoker,
            boolean acceptsPets,
            GenderPreference genderPreference,
            Set<Long> hobbies,
            /** As written on the profile, such as "ФИНКИ" or "Факултет за … (ФИНКИ), Скопје". */
            String faculty,
            /** The place this person offers. Only someone who has a place and lists it has one. */
            ListedPlace place) {
    }

    /**
     * Parts are percentages. Rent is null for dorms, where it does not count. A different room type in
     * a dorm where that matters does not lower the total; such matches are listed after the others.
     */
    public record Score(int total, int daily, int together, Integer rent, int hobbies, int age,
                        boolean differentRoomType, boolean sameFaculty) {
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
        var w = properties.dorm();
        double daily = daily(a, b);
        double together = together(a, b);
        double hobbies = hobbies(a, b);
        double age = age(a.age(), b.age());
        double total = w.daily() * daily + w.together() * together + w.hobbies() * hobbies + w.age() * age;
        boolean differentRoomType = a.roomTypeAffectsMatch() && !sameRoomType(a, b);
        return Optional.of(finish(total, daily, together, null, hobbies, age, differentRoomType, a, b));
    }

    private Optional<Score> scoreApartment(Person a, Person b) {
        if (!situationsFit(a.situation(), b.situation()) || !listedIfOffering(a) || !listedIfOffering(b)
                || !sharePropertyType(a, b) || !shareArea(a, b)) {
            return Optional.empty();
        }
        OptionalDouble rent = rent(a, b);
        if (rent.isEmpty()) {
            return Optional.empty();
        }
        var w = properties.apartment();
        double daily = daily(a, b);
        double together = together(a, b);
        double hobbies = hobbies(a, b);
        double age = age(a.age(), b.age());
        double total = w.daily() * daily + w.together() * together + w.rent() * rent.getAsDouble()
                + w.hobbies() * hobbies + w.age() * age;
        return Optional.of(finish(total, daily, together, rent.getAsDouble(), hobbies, age, false, a, b));
    }

    private Score finish(double total, double daily, double together, Double rent, double hobbies, double age,
                         boolean differentRoomType, Person a, Person b) {
        boolean sameFaculty = sameFaculty(a.faculty(), b.faculty());
        if (sameFaculty) {
            total = Math.min(1, total + properties.sameFacultyBonus());
        }
        return new Score(percent(total), percent(daily), percent(together), rent == null ? null : percent(rent),
                percent(hobbies), percent(age), differentRoomType, sameFaculty);
    }

    // ---- requirements

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
        return (a == Situation.HAS_PLACE && b.needsPlace())
                || (b == Situation.HAS_PLACE && a.needsPlace())
                || (a.searchesTogether() && b.searchesTogether());
    }

    /** Someone who has a place is matched only once it is listed, since the listing gives its rent and location. */
    private static boolean listedIfOffering(Person person) {
        return person.situation() != Situation.HAS_PLACE || person.place() != null;
    }

    private static boolean sharePropertyType(Person a, Person b) {
        Set<PropertyType> shared = new HashSet<>(propertyTypes(a));
        shared.retainAll(propertyTypes(b));
        return !shared.isEmpty();
    }

    private static Set<PropertyType> propertyTypes(Person person) {
        return person.place() != null ? Set.of(person.place().type()) : person.propertyTypes();
    }

    /** Only the places a student chose count: nobody is shown a town or municipality they did not ask for. */
    private static boolean shareArea(Person a, Person b) {
        Set<Area> theirs = areas(b);
        return areas(a).stream().anyMatch(theirs::contains);
    }

    private static Set<Area> areas(Person person) {
        return person.place() != null ? Set.of(person.place().area()) : person.areas();
    }

    // ---- rent

    /**
     * A place cheaper than someone's budget is fine. Above it, the score falls linearly and reaches 0 at
     * 20% over the budget; further than that, the place is out of reach and the two do not match. Two
     * students searching together are judged the same way by the gap between their budgets, measured
     * against the lower of the two maximums.
     */
    static OptionalDouble rent(Person a, Person b) {
        if (a.place() != null || b.place() != null) {
            Person owner = a.place() != null ? a : b;
            Person seeker = owner == a ? b : a;
            return withinReach(owner.place().rent() - seeker.budgetMax(), seeker.budgetMax());
        }
        int gap = Math.max(a.budgetMin(), b.budgetMin()) - Math.min(a.budgetMax(), b.budgetMax());
        return withinReach(gap, Math.min(a.budgetMax(), b.budgetMax()));
    }

    private static OptionalDouble withinReach(int over, int budget) {
        if (over <= 0) {
            return OptionalDouble.of(1);
        }
        double tolerance = Math.max(1, RENT_TOLERANCE * budget);
        return over > tolerance ? OptionalDouble.empty() : OptionalDouble.of(1 - over / tolerance);
    }

    // ---- habits

    /** Guests, cleaning, sleep and sound; for a flat also smoking and pets, which a dorm does not allow. */
    private static double daily(Person a, Person b) {
        Average average = new Average()
                .add(a.guests(), b.guests(), 1)
                .add(a.cleaning(), b.cleaning(), 1)
                .add(a.sleep(), b.sleep(), 1)
                .add(a.sound(), b.sound(), 1);
        if (a.housingType() == HousingType.APARTMENT) {
            average.add(lifestyle(a.smoker(), b.smoker(), a.acceptsSmoker(), b.acceptsSmoker()), 1)
                    .add(lifestyle(a.hasPets(), b.hasPets(), a.acceptsPets(), b.acceptsPets()), 1);
        }
        return average.value();
    }

    /**
     * Communication, sharing, parties and a partner staying over. The last two count twice, since opposite
     * answers there are what most often ends a shared flat.
     */
    private static double together(Person a, Person b) {
        return new Average()
                .add(a.communication(), b.communication(), 1)
                .add(a.sharing(), b.sharing(), 1)
                .add(a.parties(), b.parties(), 2)
                .add(a.partnerStays(), b.partnerStays(), 2)
                .value();
    }

    /**
     * Smoking and pets only count against a match when one of the two minds them, and that already keeps
     * the pair apart. A difference both accept costs nothing.
     */
    private static double lifestyle(boolean aDoes, boolean bDoes, boolean aAccepts, boolean bAccepts) {
        return (!bDoes || aAccepts) && (!aDoes || bAccepts) ? 1 : 0;
    }

    /**
     * A weighted average of answer distances. On a three-step scale the same answer is 1, the next one
     * 0.5 and the opposite end 0. "Не ми е важно" (NO_PREFERENCE) leaves the question out for that pair,
     * so nobody is marked down for something they do not mind.
     */
    private static final class Average {
        private double sum;
        private double weight;

        <E extends Enum<E>> Average add(E a, E b, double questionWeight) {
            if (isNoPreference(a) || isNoPreference(b)) {
                return this;
            }
            int steps = (int) java.util.Arrays.stream(a.getDeclaringClass().getEnumConstants())
                    .filter(value -> !isNoPreference(value)).count() - 1;
            return add(1 - (double) Math.abs(a.ordinal() - b.ordinal()) / steps, questionWeight);
        }

        Average add(double value, double questionWeight) {
            sum += value * questionWeight;
            weight += questionWeight;
            return this;
        }

        double value() {
            return weight == 0 ? 1 : sum / weight;
        }

        private static boolean isNoPreference(Enum<?> value) {
            return "NO_PREFERENCE".equals(value.name());
        }
    }

    // ---- hobbies, age, faculty

    /**
     * How much of each student's hobbies the other one shares, averaged. Someone with many hobbies is
     * not marked down much for the extra ones: 3 shared out of 3 and 9 gives (100% + 33%) / 2 = 67%.
     */
    private static double hobbies(Person a, Person b) {
        if (a.hobbies().isEmpty() || b.hobbies().isEmpty()) {
            return 0;
        }
        Set<Long> shared = new HashSet<>(a.hobbies());
        shared.retainAll(b.hobbies());
        return ((double) shared.size() / a.hobbies().size() + (double) shared.size() / b.hobbies().size()) / 2;
    }

    /**
     * Up to two years apart is the same stage of studies; up to five still overlaps; beyond that a first
     * year and a graduate student usually live differently.
     */
    static double age(int a, int b) {
        int difference = Math.abs(a - b);
        return difference <= 2 ? 1 : difference <= 5 ? 0.6 : 0.3;
    }

    private static final Pattern ABBREVIATION = Pattern.compile("\\(([^)]+)\\)");

    /**
     * The faculty is chosen from a list or typed, so "ФИНКИ" and "Факултет за … (ФИНКИ), Скопје" are the
     * same faculty: both the whole name and the abbreviation in brackets are compared.
     */
    static boolean sameFaculty(String a, String b) {
        Set<String> first = facultyKeys(a);
        return !first.isEmpty() && facultyKeys(b).stream().anyMatch(first::contains);
    }

    private static Set<String> facultyKeys(String faculty) {
        if (faculty == null || faculty.isBlank()) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        keys.add(faculty.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
        Matcher matcher = ABBREVIATION.matcher(faculty);
        while (matcher.find()) {
            keys.add(matcher.group(1).trim().toLowerCase(Locale.ROOT));
        }
        return keys;
    }

    private static boolean sameRoomType(Person a, Person b) {
        return a.roomOptionId() != null && a.roomOptionId().equals(b.roomOptionId());
    }

    private static int percent(double value) {
        return (int) Math.round(value * 100);
    }
}
