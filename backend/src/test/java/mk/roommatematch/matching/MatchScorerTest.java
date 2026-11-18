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
import org.junit.jupiter.api.Test;

class MatchScorerTest {

    private static final long GOCE_DELCEV = 1;
    private static final long KITCHEN = 10;
    private static final long TOILET = 11;

    private final MatchScorer scorer = new MatchScorer(new MatchingProperties(
            new ApartmentWeights(0.25, 0.20, 0.30, 0.15, 0.10), new DormWeights(0.35, 0.35, 0.20, 0.10), 0.05));

    /** A mutable draft of {@link Person}, so each test states only what it is about. */
    private static class Draft {
        long id = 1;
        Gender gender = Gender.FEMALE;
        int age = 21;
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
        CleaningStyle cleaning = CleaningStyle.SCHEDULE;
        SleepSchedule sleep = SleepSchedule.FLEXIBLE;
        SoundLevel sound = SoundLevel.QUIET_SPEAKER;
        CommunicationLevel communication = CommunicationLevel.OCCASIONAL;
        SharingStyle sharing = SharingStyle.BASICS;
        PartyAttitude parties = PartyAttitude.SMALL;
        PartnerStays partnerStays = PartnerStays.SOMETIMES;
        boolean acceptsSmoker = true;
        boolean acceptsPets = true;
        GenderPreference preference = GenderPreference.ANY;
        Set<Long> hobbies = Set.of(1L, 2L);
        String faculty = "";
        ListedPlace place;

        Person build() {
            return new Person(id, gender, age, housing, situation, dormId, roomOptionId, roomTypeMatters, min, max, areas,
                    types, smoker, pets, guests, cleaning, sleep, sound, communication, sharing, parties, partnerStays, acceptsSmoker, acceptsPets, preference,
                    hobbies, faculty, place);
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
        assertThat(score.daily()).isEqualTo(100);
        assertThat(score.together()).isEqualTo(100);
        assertThat(score.rent()).isEqualTo(100);
        assertThat(score.hobbies()).isEqualTo(100);
        assertThat(score.age()).isEqualTo(100);
    }

    @Test
    void scoreIsTheSameInBothDirections() {
        Person a = person(1, d -> { d.cleaning = CleaningStyle.REGULAR; d.guests = GuestsFrequency.OFTEN; d.max = 150; });
        Person b = person(2, d -> { d.min = 180; d.max = 300; d.areas = Set.of(Area.BUTEL, Area.KARPOSH); d.hobbies = Set.of(2L, 3L); });

        assertThat(score(a, b)).isEqualTo(score(b, a));
    }

    @Test
    void nobodyMatchesThemselves() {
        assertThat(matches(person(1, d -> { }), person(1, d -> { }))).isFalse();
    }

    @Test
    void weightsCombineThePartsAsConfigured() {
        // Everything 100 except hobbies, which are 0 of 15 points.
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
    void anAcceptedSmokerOrPetDoesNotLowerTheScore() {
        Person accepting = person(1, d -> { });
        Score same = score(accepting, person(2, d -> { }));

        assertThat(score(accepting, person(3, d -> d.smoker = true))).isEqualTo(same);
        assertThat(score(accepting, person(4, d -> d.pets = true))).isEqualTo(same);
        assertThat(same.daily()).isEqualTo(100);
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
        Person has = owner(1, 150);
        Person alsoHas = owner(2, 150);
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
    void someoneOpenToBothMatchesOwnersAndOtherSearchers() {
        Person both = person(1, d -> d.situation = Situation.NEEDS_OR_TOGETHER);
        Person alsoBoth = person(2, d -> d.situation = Situation.NEEDS_OR_TOGETHER);

        assertThat(matches(both, owner(3, 150))).isTrue();
        assertThat(matches(both, person(4, d -> d.situation = Situation.SEARCH_TOGETHER))).isTrue();
        assertThat(matches(both, alsoBoth)).isTrue();
        // Two people who both need someone with a place cannot help each other.
        assertThat(matches(both, person(5, d -> d.situation = Situation.NEEDS_PLACE))).isFalse();
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

        assertThat(score(seeker, houseInBitola).rent()).isEqualTo(100);
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
    void anotherSkopjeMunicipalityThatWasNotChosenDoesNotMatch() {
        Person centar = person(1, d -> d.areas = Set.of(Area.CENTAR));

        assertThat(matches(centar, person(2, d -> d.areas = Set.of(Area.CHAIR)))).isFalse();
        assertThat(matches(centar, person(3, d -> d.areas = Set.of(Area.CENTAR)))).isTrue();
    }

    @Test
    void aPlaceOutsideTheSeekersAreasDoesNotMatch() {
        Person seeker = person(1, d -> { d.situation = Situation.NEEDS_PLACE; d.areas = Set.of(Area.KARPOSH, Area.CENTAR); });
        Person ownerInChair = person(2, d -> {
            d.situation = Situation.HAS_PLACE;
            d.areas = Set.of(Area.CHAIR);
            d.place = new ListedPlace(PropertyType.APARTMENT, Area.CHAIR, 150);
        });
        Person ownerInKarposh = person(3, d -> {
            d.situation = Situation.HAS_PLACE;
            d.areas = Set.of(Area.CHAIR);
            d.place = new ListedPlace(PropertyType.APARTMENT, Area.KARPOSH, 150);
        });

        assertThat(matches(seeker, ownerInChair)).isFalse();
        // The listing decides where the owner's place is, whatever the owner's own profile says.
        assertThat(matches(seeker, ownerInKarposh)).isTrue();
    }

    @Test
    void oneSharedAreaAmongSeveralIsEnough() {
        assertThat(matches(person(1, d -> d.areas = Set.of(Area.BITOLA, Area.STIP)),
                person(2, d -> d.areas = Set.of(Area.STIP, Area.OHRID)))).isTrue();
    }

    @Test
    void rentIsFullWhenBudgetsOverlapAndFallsToNothingAtTwentyPercentOver() {
        // The lower maximum is 200, so the budgets may be up to 40 apart.
        Person cheap = person(1, d -> { d.min = 100; d.max = 200; });

        assertThat(score(cheap, person(2, d -> { d.min = 200; d.max = 300; })).rent()).isEqualTo(100);
        assertThat(score(cheap, person(3, d -> { d.min = 220; d.max = 300; })).rent()).isEqualTo(50);
        assertThat(score(cheap, person(4, d -> { d.min = 240; d.max = 300; })).rent()).isZero();
        assertThat(matches(cheap, person(5, d -> { d.min = 250; d.max = 300; }))).isFalse();
    }

    @Test
    void aListedRentMoreThanTwentyPercentOverTheBudgetIsNoMatch() {
        Person seeker = person(1, d -> { d.situation = Situation.NEEDS_PLACE; d.min = 50; d.max = 100; });

        assertThat(score(seeker, owner(2, 80)).rent()).isEqualTo(100);
        assertThat(score(seeker, owner(3, 110)).rent()).isEqualTo(50);
        assertThat(score(seeker, owner(4, 120)).rent()).isZero();
        assertThat(matches(seeker, owner(5, 121))).isFalse();
        assertThat(matches(owner(5, 121), seeker)).isFalse();
    }

    private static Person owner(long id, int rent) {
        return person(id, d -> {
            d.situation = Situation.HAS_PLACE;
            d.place = new ListedPlace(PropertyType.APARTMENT, Area.KARPOSH, rent);
        });
    }

    @Test
    void someoneWithAPlaceIsMatchedOnlyOnceItIsListed() {
        Person seeker = person(1, d -> d.situation = Situation.NEEDS_PLACE);
        Person unlisted = person(2, d -> {
            d.situation = Situation.HAS_PLACE;
            d.min = null;
            d.max = null;
            d.areas = Set.of();
        });

        assertThat(matches(seeker, unlisted)).isFalse();
        assertThat(matches(unlisted, seeker)).isFalse();
        assertThat(matches(seeker, owner(3, 150))).isTrue();
    }

    @Test
    void ageCloseByCountsFullyAndFallsWithTheGap() {
        Person young = person(1, d -> d.age = 19);

        assertThat(score(young, person(2, d -> d.age = 21)).age()).isEqualTo(100);
        assertThat(score(young, person(3, d -> d.age = 24)).age()).isEqualTo(60);
        assertThat(score(young, person(4, d -> d.age = 30)).age()).isEqualTo(30);
        // Age weighs a tenth: 60% of it costs 4 points.
        assertThat(score(young, person(3, d -> d.age = 24)).total()).isEqualTo(96);
    }

    @Test
    void noPreferenceNeverLowersTheMatch() {
        Person a = person(1, d -> {
            d.sound = SoundLevel.HEADPHONES;
            d.communication = CommunicationLevel.MINIMAL;
            d.sharing = SharingStyle.SEPARATE;
        });
        Person easygoing = person(2, d -> {
            d.sound = SoundLevel.NO_PREFERENCE;
            d.communication = CommunicationLevel.NO_PREFERENCE;
            d.sharing = SharingStyle.NO_PREFERENCE;
        });
        Person opposite = person(3, d -> {
            d.sound = SoundLevel.LOUD;
            d.communication = CommunicationLevel.TOGETHER;
            d.sharing = SharingStyle.EVERYTHING;
        });

        Score score = score(a, easygoing);
        assertThat(score.daily()).isEqualTo(100);
        assertThat(score.together()).isEqualTo(100);
        assertThat(score.total()).isEqualTo(100);
        assertThat(score(a, opposite).total()).isLessThan(100);
    }

    @Test
    void hobbiesAverageHowMuchOfEachOnesHobbiesIsShared() {
        // One shared: a third of the first's and half of the second's.
        Score score = score(person(1, d -> d.hobbies = Set.of(1L, 2L, 3L)), person(2, d -> d.hobbies = Set.of(3L, 4L)));

        assertThat(score.hobbies()).isEqualTo(42);
    }

    @Test
    void manyExtraHobbiesDoNotPullTheScoreDownMuch() {
        // All three of the first student's hobbies are among the nine of the second.
        Score score = score(person(1, d -> d.hobbies = Set.of(1L, 2L, 3L)),
                person(2, d -> d.hobbies = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)));

        assertThat(score.hobbies()).isEqualTo(67);
    }

    @Test
    void dormDailyLifeLeavesOutSmokingAndPets() {
        Person a = person(1, dorm(GOCE_DELCEV, null, false).andThen(d -> d.cleaning = CleaningStyle.WHEN_POSSIBLE));
        Person b = person(2, dorm(GOCE_DELCEV, null, false).andThen(d -> d.cleaning = CleaningStyle.REGULAR));

        // Cleaning is one of four everyday questions in a dorm, so opposite answers cost a quarter.
        assertThat(score(a, b).daily()).isEqualTo(75);
    }

    @Test
    void partiesAndAPartnerStayingOverCountTwiceAsMuch() {
        Person quiet = person(1, dorm(GOCE_DELCEV, null, false).andThen(d -> {
            d.parties = PartyAttitude.NONE;
            d.communication = CommunicationLevel.MINIMAL;
        }));
        Person samePartiesOtherTalk = person(2, dorm(GOCE_DELCEV, null, false).andThen(d -> {
            d.parties = PartyAttitude.NONE;
            d.communication = CommunicationLevel.TOGETHER;
        }));
        Person otherPartiesSameTalk = person(3, dorm(GOCE_DELCEV, null, false).andThen(d -> {
            d.parties = PartyAttitude.PARTIES;
            d.communication = CommunicationLevel.MINIMAL;
        }));

        // Living together weighs communication 1, sharing 1, parties 2 and partner 2.
        assertThat(score(quiet, samePartiesOtherTalk).together()).isEqualTo(83);
        assertThat(score(quiet, otherPartiesSameTalk).together()).isEqualTo(67);
    }

    @Test
    void theSameFacultyRaisesTheMatch() {
        Person finki = person(1, d -> d.faculty = "ФИНКИ");
        Score other = score(finki, person(2, d -> d.faculty = "ФЕИТ"));
        Score same = score(finki, person(3, d -> d.faculty = "Факултет за информатички науки и компјутерско инженерство (ФИНКИ), Скопје"));

        assertThat(same.sameFaculty()).isTrue();
        assertThat(other.sameFaculty()).isFalse();
        assertThat(same.total()).isEqualTo(Math.min(100, other.total() + 5));
    }

    @Test
    void facultiesAreComparedByNameOrAbbreviation() {
        assertThat(MatchScorer.sameFaculty("  финки ", "ФИНКИ")).isTrue();
        assertThat(MatchScorer.sameFaculty("Машински факултет", "Машински  факултет")).isTrue();
        assertThat(MatchScorer.sameFaculty("ФИНКИ", "ФЕИТ")).isFalse();
        assertThat(MatchScorer.sameFaculty("", "")).isFalse();
    }

    @Test
    void nearbyAnswersCountHalf() {
        Person a = person(1, d -> d.sound = SoundLevel.HEADPHONES);

        assertThat(score(a, person(2, d -> d.sound = SoundLevel.QUIET_SPEAKER)).daily())
                .isGreaterThan(score(a, person(3, d -> d.sound = SoundLevel.LOUD)).daily());
    }

    @Test
    void dormIgnoresRentAndUsesItsOwnWeights() {
        Person a = person(1, dorm(GOCE_DELCEV, null, false));
        Person b = person(2, dorm(GOCE_DELCEV, null, false).andThen(d -> d.hobbies = Set.of(9L)));

        Score score = score(a, b);
        assertThat(score.rent()).isNull();
        assertThat(score.total()).isEqualTo(80);
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
    void differentRoomTypeIsMarkedButDoesNotLowerTheScore() {
        Person kitchen = person(1, dorm(GOCE_DELCEV, KITCHEN, true));

        Score same = score(kitchen, person(2, dorm(GOCE_DELCEV, KITCHEN, true)));
        Score different = score(kitchen, person(3, dorm(GOCE_DELCEV, TOILET, true)));

        assertThat(same.total()).isEqualTo(100);
        assertThat(same.differentRoomType()).isFalse();
        assertThat(different.total()).isEqualTo(100);
        assertThat(different.differentRoomType()).isTrue();
    }

    @Test
    void differentRoomTypeChangesNothingWhereItDoesNotMatter() {
        Score score = score(person(1, dorm(2, 20L, false)), person(2, dorm(2, 21L, false)));

        assertThat(score.total()).isEqualTo(100);
        assertThat(score.differentRoomType()).isFalse();
    }
}
