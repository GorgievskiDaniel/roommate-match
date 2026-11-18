package mk.roommatematch.matching;

import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import mk.roommatematch.chat.BlockRepository;
import mk.roommatematch.listing.Listing;
import mk.roommatematch.listing.ListingRepository;
import mk.roommatematch.matching.MatchScorer.ListedPlace;
import mk.roommatematch.matching.MatchScorer.Person;
import mk.roommatematch.matching.MatchScorer.Score;
import mk.roommatematch.profile.Profile;
import mk.roommatematch.profile.ProfileDtos.ProfileSummary;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.profile.ProfileRepository;
import mk.roommatematch.profile.ProfileService;
import mk.roommatematch.reference.Area;
import mk.roommatematch.reference.Hobby;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MatchService {

    public record MatchResponse(
            ProfileSummary profile,
            Score score,
            String situation,
            String roomOption,
            List<String> areas,
            /** Set when this person lives in a place that is listed with photos. */
            Long listingId) {
    }

    private final ProfileRepository profiles;
    private final ListingRepository listings;
    private final BlockRepository blocks;
    private final MatchScorer scorer;

    public MatchService(ProfileRepository profiles, ListingRepository listings, BlockRepository blocks,
                        MatchScorer scorer) {
        this.profiles = profiles;
        this.listings = listings;
        this.blocks = blocks;
        this.scorer = scorer;
    }

    /** Someone blocked, or who blocked this student, is never suggested to either of them. */
    private Set<Long> blockedEitherWay(Long userId) {
        Set<Long> result = new HashSet<>();
        blocks.findByBlockerId(userId).forEach(block -> result.add(block.getBlockedId()));
        blocks.findByBlockedId(userId).forEach(block -> result.add(block.getBlockerId()));
        return result;
    }

    /**
     * Only someone who has a place offers it. A student looking for a flat may also have posted a
     * listing, but is matched by what they are looking for, not by that listing.
     */
    private static Listing offered(Profile profile, Listing listing) {
        return profile.getSituation() == Situation.HAS_PLACE ? listing : null;
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> matchesFor(Long userId) {
        Map<Long, Listing> listingByResident = new HashMap<>();
        listings.findAll().forEach(listing ->
                listing.getResidentIds().forEach(resident -> listingByResident.put(resident, listing)));
        Profile me = profiles.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Профилот сè уште не е пополнет."));
        Person mine = toPerson(me, offered(me, listingByResident.get(userId)));
        Set<Long> blocked = blockedEitherWay(userId);
        return profiles.findAll().stream()
                .filter(other -> !blocked.contains(other.getUserId()))
                .flatMap(other -> {
                    Listing listing = offered(other, listingByResident.get(other.getUserId()));
                    Optional<Score> score = scorer.score(mine, toPerson(other, listing));
                    return score.stream().map(found -> toResponse(other, found, listing));
                })
                // A different room type, where it matters, puts a match after the others instead of halving it.
                .sorted(Comparator.comparing((MatchResponse m) -> m.score().differentRoomType())
                        .thenComparing(Comparator.comparingInt((MatchResponse m) -> m.score().total()).reversed())
                        .thenComparing(m -> m.profile().userId()))
                .toList();
    }

    /** Everything the explanation may mention. Names and contact details are left out on purpose. */
    @Transactional(readOnly = true)
    public Optional<PairFacts> factsFor(Long viewerId, Long otherId) {
        Optional<Profile> viewer = profiles.findById(viewerId);
        Optional<Profile> other = profiles.findById(otherId);
        if (viewer.isEmpty() || other.isEmpty()) {
            return Optional.empty();
        }
        if (blockedEitherWay(viewerId).contains(otherId)) {
            return Optional.empty();
        }
        Listing viewerListing = offered(viewer.get(), listings.findByResident(viewerId).orElse(null));
        Listing otherListing = offered(other.get(), listings.findByResident(otherId).orElse(null));
        return scorer.score(toPerson(viewer.get(), viewerListing), toPerson(other.get(), otherListing))
                .map(score -> new PairFacts(score, toSide(viewer.get(), viewerListing), toSide(other.get(), otherListing),
                        sharedHobbies(viewer.get(), other.get()), other.get().getFirstName()));
    }

    /** The gender is there so the text uses the right grammatical forms, since the name is never sent. */
    /** Smoking and pets are null in a dorm, where neither is allowed. */
    public record Side(String gender, Boolean smoker, Boolean hasPets, String guests, String cleaning, String sleep,
                       String sound, String communication, String sharing, String parties, String partnerStays,
                       List<String> hobbies, Integer rentMin, Integer rentMax, List<String> places) {
    }

    public record PairFacts(Score score, Side reader, Side other, List<String> sharedHobbies, String otherFirstName) {
    }

    private static Side toSide(Profile p, Listing listing) {
        Set<Area> areas = listing != null ? Set.of(listing.getArea()) : p.getAreas();
        return new Side(
                p.getGender() == null ? null : p.getGender().name(),
                p.getHousingType() == HousingType.DORM ? null : p.isSmoker(),
                p.getHousingType() == HousingType.DORM ? null : p.isHasPets(),
                p.getGuestsFrequency().name(),
                p.getCleaning().name(),
                p.getSleepSchedule().name(),
                p.getSound().name(),
                p.getCommunication().name(),
                p.getSharing().name(),
                p.getParties().name(),
                p.getPartnerStays().name(),
                p.getHobbies().stream().map(Hobby::getName).sorted().toList(),
                listing != null ? Integer.valueOf(listing.getMonthlyRent()) : p.getBudgetMin(),
                listing != null ? Integer.valueOf(listing.getMonthlyRent()) : p.getBudgetMax(),
                areas.stream().sorted().map(Area::getLabel).toList());
    }

    private static List<String> sharedHobbies(Profile a, Profile b) {
        Set<Long> theirs = b.getHobbies().stream().map(Hobby::getId).collect(Collectors.toSet());
        return a.getHobbies().stream().filter(h -> theirs.contains(h.getId())).map(Hobby::getName).sorted().toList();
    }

    private static MatchResponse toResponse(Profile profile, Score score, Listing listing) {
        Set<Area> areas = listing != null ? Set.of(listing.getArea()) : profile.getAreas();
        return new MatchResponse(
                ProfileService.toSummary(profile),
                score,
                profile.getSituation() == null ? null : profile.getSituation().name(),
                profile.getDormRoomOption() == null ? null : profile.getDormRoomOption().getName(),
                areas.stream().sorted().map(Area::getLabel).toList(),
                listing != null && !listing.getImages().isEmpty() ? listing.getId() : null);
    }

    private static Person toPerson(Profile p, Listing listing) {
        return new Person(
                p.getUserId(),
                p.getGender(),
                Period.between(p.getBirthDate(), LocalDate.now()).getYears(),
                p.getHousingType(),
                p.getSituation(),
                p.getDorm() == null ? null : p.getDorm().getId(),
                p.getDormRoomOption() == null ? null : p.getDormRoomOption().getId(),
                p.getDorm() != null && p.getDorm().isRoomTypeAffectsMatch(),
                p.getBudgetMin(),
                p.getBudgetMax(),
                p.getAreas(),
                p.getPropertyTypes(),
                p.isSmoker(),
                p.isHasPets(),
                p.getGuestsFrequency(),
                p.getCleaning(),
                p.getSleepSchedule(),
                p.getSound(),
                p.getCommunication(),
                p.getSharing(),
                p.getParties(),
                p.getPartnerStays(),
                p.isAcceptsSmoker(),
                p.isAcceptsPets(),
                p.getRoommateGenderPreference(),
                p.getHobbies().stream().map(Hobby::getId).collect(Collectors.toSet()),
                p.getFaculty(),
                listing == null ? null : new ListedPlace(listing.getPropertyType(), listing.getArea(), listing.getMonthlyRent()));
    }
}
