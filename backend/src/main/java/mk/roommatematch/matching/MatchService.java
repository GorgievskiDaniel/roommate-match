package mk.roommatematch.matching;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import mk.roommatematch.listing.Listing;
import mk.roommatematch.listing.ListingRepository;
import mk.roommatematch.matching.MatchScorer.ListedPlace;
import mk.roommatematch.matching.MatchScorer.Person;
import mk.roommatematch.matching.MatchScorer.Score;
import mk.roommatematch.profile.Profile;
import mk.roommatematch.profile.ProfileDtos.ProfileSummary;
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
    private final MatchScorer scorer;

    public MatchService(ProfileRepository profiles, ListingRepository listings, MatchScorer scorer) {
        this.profiles = profiles;
        this.listings = listings;
        this.scorer = scorer;
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> matchesFor(Long userId) {
        Map<Long, Listing> listingByResident = new HashMap<>();
        listings.findAll().forEach(listing ->
                listing.getResidentIds().forEach(resident -> listingByResident.put(resident, listing)));
        Profile me = profiles.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Профилот сè уште не е пополнет."));
        Person mine = toPerson(me, listingByResident.get(userId));
        return profiles.findAll().stream()
                .flatMap(other -> {
                    Listing listing = listingByResident.get(other.getUserId());
                    Optional<Score> score = scorer.score(mine, toPerson(other, listing));
                    return score.stream().map(found -> toResponse(other, found, listing));
                })
                .sorted(Comparator.comparingInt((MatchResponse m) -> m.score().total()).reversed()
                        .thenComparing(m -> m.profile().userId()))
                .toList();
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
                p.getTidiness(),
                p.getSleepSchedule(),
                p.getQuietNeed(),
                p.isAcceptsSmoker(),
                p.isAcceptsPets(),
                p.getRoommateGenderPreference(),
                p.getHobbies().stream().map(Hobby::getId).collect(Collectors.toSet()),
                listing == null ? null : new ListedPlace(listing.getPropertyType(), listing.getArea(), listing.getMonthlyRent()));
    }
}
