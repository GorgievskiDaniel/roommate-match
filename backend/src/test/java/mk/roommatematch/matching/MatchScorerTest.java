package mk.roommatematch.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.function.Consumer;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.matching.MatchScorer.ListedPlace;
import mk.roommatematch.matching.MatchScorer.Person;
import mk.roommatematch.matching.MatchScorer.Score;
import mk.roommatematch.matching.MatchingProperties.ApartmentWeights;
import mk.roommatematch.matching.MatchingProperties.DormWeights;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileEnums.GenderPreference;
import mk.roommatematch.profile.ProfileEnums.GuestsFrequency;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.SleepSchedule;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.reference.Area;
import org.junit.jupiter.api.Test;

class MatchScorerTest {

    private static final long GOCE_DELCEV = 1;
    private static final long KITCHEN = 10;
    private static final long TOILET = 11;

    private final MatchScorer scorer = new MatchScorer(new MatchingProperties(
            new ApartmentWeights(0.35, 0.25, 0.25, 0.15), new DormWeights(0.70, 0.30), 0.5));

    /** A mutable draft of {@link Person}, so each test states only what it is about. */
    private static class Draft {
        long id = 1;
        Gender gender = Gender.FEMALE;
        HousingType housing = HousingType.APARTMENT;
        Situation situation = Situation.SEARCH_TOGETHER;
        Long dormId;
        Long roomOptionId;
        boolean roomTypeMatters;
        Integer min = 100;
        Integer max = 200;
        Set<Area> areas = Set.of(Area.KARPOSH);
        Set<PropertyType> types = Set.of(PropertyType.APARTMENT, PropertyType.HOUSE);
        boolean smoker;
        boolean pets;
        GuestsFrequency guests = GuestsFrequency.RARELY;
        int tidiness = 3;
        SleepSchedule sleep = SleepSchedule.FLEXIBLE;
        int quiet = 3;
        boolean acceptsSmoker = true;
        boolean acceptsPets = true;
        GenderPreference preference = GenderPreference.ANY;
        Set<Long> hobbies = Set.of(1L, 2L);
        ListedPlace place;

        Person build() {
            return new Person(id, gender, housing, situation, dormId, roomOptionId, roomTypeMatters, min, max, areas,
                    types, smoker, pets, guests, tidiness, sleep, quiet, acceptsSmoker, acceptsPets, preference,
                    hobbies, place);
        }
    }

    private static Person person(long id, Consumer<Draft> changes) {
        Draft draft = new Draft();
        draft.id = id;
        changes.accept(draft);
        return draft.build();
    }

    private static Consumer<Draft> dorm(long dormId, Long roomOption, boolean roomTypeMatters) {
        return d -> {
            d.housing = HousingType.DORM;
            d.situation = null;
            d.dormId = dormId;
            d.roomOptionId = roomOption;
            d.roomTypeMatters = roomTypeMatters;
            d.min = null;
            d.max = null;
            d.areas = Set.of();
            d.types = Set.of();
        };
    }

    private Score score(Person a, Person b) {
        return scorer.score(a, b).orElseThrow();
    }

    private boolean matches(Person a, Person b) {
        return scorer.score(a, b).isPresent();
    }

    @Test
    void identicalPeopleScoreOneHundred() {
        Score score = score(person(1, d -> { }), person(2, d -> { }));

        assertThat(score.total()).isEqualTo(100);
        assertThat(score.habits()).isEqualTo(100);
        assertThat(score.rent()).isEqualTo(100);
        assertThat(score.location()).isEqualTo(100);
        assertThat(score.hobbies()).isEqualTo(100);
    }

    @Test
    void scoreIsTheSameInBothDirections() {
        Person a = person(1, d -> { d.tidiness = 5; d.guests = GuestsFrequency.OFTEN; d.max = 150; });
        Person b = person(2, d -> { d.min = 180; d.max = 300; d.areas = Set.of(Area.BUTEL); d.hobbies = Set.of(2L, 3L); });

        assertThat(score(a, b)).isEqualTo(score(b, a));
    }

    @Test
    void nobodyMatchesThemselves() {
        assertThat(matches(person(1, d -> { }), person(1, d -> { }))).isFalse();
    }

    @Test
    void weightsCombineThePartsAsConfigured() {
        // Habits 100, rent 100, location 100, hobbies 0 of 15 points.
        Score score = score(person(1, d -> { }), person(2, d -> d.hobbies = Set.of(9L)));

        assertThat(score.hobbies()).isZero();
        assertThat(score.total()).isEqualTo(85);
    }

    @Test
    void smokerIsExcludedWhenTheOtherDoesNotAcceptSmokers() {
        Person ana = person(1, d -> d.acceptsSmoker = false);
        Person marko = person(2, d -> d.smoker = true);

        assertThat(matches(ana, marko)).isFalse();
        assertThat(matches(marko, ana)).isFalse();
    }

    @Test
    void petsAreExcludedWhenTheOtherDoesNotAcceptPets() {
        assertThat(matches(person(1, d -> d.acceptsPets = false), person(2, d -> d.pets = true))).isFalse();
    }

    @Test
    void acceptedDifferenceLowersHabitsInsteadOfExcluding() {
        Score score = score(person(1, d -> { }), person(2, d -> d.smoker = true));

        assertThat(score.habits()).isLessThan(100);
    }

    @Test
    void genderPreferenceMustHoldForBothSides() {
        Person same = person(1, d -> d.preference = GenderPreference.SAME);
        Person man = person(2, d -> d.gender = Gender.MALE);

        assertThat(matches(same, man)).isFalse();
        assertThat(matches(man, same)).isFalse();
        assertThat(matches(same, person(3, d -> { }))).isTrue();
    }

    @Test
    void situationsPairUpOnlyAsTheyCanLiveTogether() {
        Person has = person(1, d -> d.situation = Situation.HAS_PLACE);
        Person alsoHas = person(2, d -> d.situation = Situation.HAS_PLACE);
        Person needs = person(3, d -> d.situation = Situation.NEEDS_PLACE);
        Person alsoNeeds = person(4, d -> d.situation = Situation.NEEDS_PLACE);
        Person together = person(5, d -> d.situation = Situation.SEARCH_TOGETHER);
        Person alsoTogether = person(6, d -> d.situation = Situation.SEARCH_TOGETHER);

        assertThat(matches(has, needs)).isTrue();
        assertThat(matches(together, alsoTogether)).isTrue();
        assertThat(matches(has, alsoHas)).isFalse();
        assertThat(matches(needs, alsoNeeds)).isFalse();
        assertThat(matches(has, together)).isFalse();
        assertThat(matches(needs, together)).isFalse();
    }

    @Test
    void propertyTypesMustOverlap() {
        Person apartment = person(1, d -> d.types = Set.of(PropertyType.APARTMENT));
        Person house = person(2, d -> d.types = Set.of(PropertyType.HOUSE));
        Person either = person(3, d -> { });

        assertThat(matches(apartment, house)).isFalse();
        assertThat(matches(apartment, either)).isTrue();
        assertThat(matches(house, either)).isTrue();
    }

    @Test
    void listedPlaceDecidesTypeAreaAndRentOverWhatTheProfileSays() {
        Person seeker = person(1, d -> {
            d.situation = Situation.NEEDS_PLACE;
            d.types = Set.of(PropertyType.HOUSE);
            d.areas = Set.of(Area.BITOLA);
            d.min = 50;
            d.max = 100;
        });
        Person houseInBitola = person(2, d -> {
            d.situation = Situation.HAS_PLACE;
            d.types = Set.of(PropertyType.APARTMENT);
            d.areas = Set.of(Area.STIP);
            d.place = new ListedPlace(PropertyType.HOUSE, Area.BITOLA, 80);
        });
        Person apartmentInBitola = person(3, d -> {
            d.situation = Situation.HAS_PLACE;
            d.place = new ListedPlace(PropertyType.APARTMENT, Area.BITOLA, 80);
        });

        Score score = score(seeker, houseInBitola);
        assertThat(score.location()).isEqualTo(100);
        assertThat(score.rent()).isEqualTo(100);
        assertThat(matches(seeker, apartmentInBitola)).isFalse();
    }

    @Test
    void differentTownsNeverMatch() {
        assertThat(matches(person(1, d -> d.areas = Set.of(Area.BITOLA)),
                person(2, d -> d.areas = Set.of(Area.KUMANOVO)))).isFalse();
        assertThat(matches(person(1, d -> d.areas = Set.of(Area.BITOLA)),
                person(2, d -> d.areas = Set.of(Area.KARPOSH)))).isFalse();
    }

    @Test
    void skopjeMunicipalitiesMatchForLessTheFartherApartTheyAre() {
        Person centar = person(1, d -> d.areas = Set.of(Area.CENTAR));
        int near = score(centar, person(2, d -> d.areas = Set.of(Area.CHAIR))).location();
        int far = score(centar, person(3, d -> d.areas = Set.of(Area.SARAJ))).location();
        int same = score(centar, person(4, d -> d.areas = Set.of(Area.CENTAR))).location();

        assertThat(same).isEqualTo(100);
        assertThat(near).isLessThanOrEqualTo(80).isGreaterThan(far);
        assertThat(far).isGreaterThanOrEqualTo(30);
    }

    @Test
    void oneSharedAreaAmongSeveralGivesFullLocationScore() {
        Score score = score(person(1, d -> d.areas = Set.of(Area.BITOLA, Area.STIP)),
                person(2, d -> d.areas = Set.of(Area.STIP, Area.OHRID)));

        assertThat(score.location()).isEqualTo(100);
    }

    @Test
    void rentIsFullWhenBudgetsOverlapAndFallsWithTheGap() {
        Person cheap = person(1, d -> { d.min = 100; d.max = 200; });

        assertThat(score(cheap, person(2, d -> { d.min = 200; d.max = 300; })).rent()).isEqualTo(100);
        assertThat(score(cheap, person(3, d -> { d.min = 250; d.max = 300; })).rent()).isEqualTo(83);
        assertThat(score(cheap, person(4, d -> { d.min = 300; d.max = 400; })).rent()).isEqualTo(75);
    }

    @Test
    void hobbiesUseSharedOutOfAll() {
        Score score = score(person(1, d -> d.hobbies = Set.of(1L, 2L, 3L)), person(2, d -> d.hobbies = Set.of(3L, 4L)));

        assertThat(score.hobbies()).isEqualTo(25);
    }

    @Test
    void dormIgnoresRentAndLocationAndUsesItsOwnWeights() {
        Person a = person(1, dorm(GOCE_DELCEV, null, false));
        Person b = person(2, dorm(GOCE_DELCEV, null, false).andThen(d -> d.hobbies = Set.of(9L)));

        Score score = score(a, b);
        assertThat(score.rent()).isNull();
        assertThat(score.location()).isNull();
        assertThat(score.total()).isEqualTo(70);
    }

    @Test
    void dormRequiresSameDormAndSameGender() {
        Person a = person(1, dorm(1, null, false));

        assertThat(matches(a, person(2, dorm(2, null, false)))).isFalse();
        assertThat(matches(a, person(3, dorm(1, null, false).andThen(d -> d.gender = Gender.MALE)))).isFalse();
        assertThat(matches(a, person(4, dorm(1, null, false)))).isTrue();
    }

    @Test
    void dormStudentDoesNotMatchApartmentSeeker() {
        assertThat(matches(person(1, dorm(1, null, false)), person(2, d -> { }))).isFalse();
    }

    @Test
    void differentRoomTypeHalvesTheScoreWhereItMatters() {
        Person kitchen = person(1, dorm(GOCE_DELCEV, KITCHEN, true));

        Score same = score(kitchen, person(2, dorm(GOCE_DELCEV, KITCHEN, true)));
        Score different = score(kitchen, person(3, dorm(GOCE_DELCEV, TOILET, true)));

        assertThat(same.total()).isEqualTo(100);
        assertThat(same.differentRoomType()).isFalse();
        assertThat(different.total()).isEqualTo(50);
        assertThat(different.differentRoomType()).isTrue();
        assertThat(different.habits()).isEqualTo(100);
    }

    @Test
    void differentRoomTypeChangesNothingWhereItDoesNotMatter() {
        Score score = score(person(1, dorm(2, 20L, false)), person(2, dorm(2, 21L, false)));

        assertThat(score.total()).isEqualTo(100);
        assertThat(score.differentRoomType()).isFalse();
    }
}
