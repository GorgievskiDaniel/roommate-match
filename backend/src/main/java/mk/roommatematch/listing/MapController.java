package mk.roommatematch.listing;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.profile.Profile;
import mk.roommatematch.profile.ProfileDtos.ProfileSummary;
import mk.roommatematch.profile.ProfileRepository;
import mk.roommatematch.profile.ProfileService;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormBlock;
import mk.roommatematch.reference.DormRepository;
import mk.roommatematch.reference.DormRoomOption;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class MapController {

    record MapListing(Long id, PropertyType propertyType, double latitude, double longitude, String areaName,
                      int monthlyRent, int freeSpots, String coverImageUrl) {
    }

    record MapBlock(String name, double latitude, double longitude, String roomOption, long seekers) {
    }

    /** A dorm with blocks is drawn as one point per block instead of a single point. */
    record MapDorm(Long id, String name, Double latitude, Double longitude, long seekers, List<MapBlock> blocks) {
    }

    record MapResponse(List<MapListing> listings, List<MapDorm> dorms) {
    }

    record DormSeeker(ProfileSummary profile, String roomOption) {
    }

    record DormResponse(Long id, String name, List<DormSeeker> seekers) {
    }

    private final ListingRepository listings;
    private final DormRepository dorms;
    private final ProfileRepository profiles;

    public MapController(ListingRepository listings, DormRepository dorms, ProfileRepository profiles) {
        this.listings = listings;
        this.dorms = dorms;
        this.profiles = profiles;
    }

    @GetMapping("/api/map")
    @Transactional(readOnly = true)
    public MapResponse map() {
        Map<Long, Long> seekers = toCounts(profiles.countByDorm());
        Map<Long, Long> seekersByOption = toCounts(profiles.countByRoomOption());
        return new MapResponse(
                listings.findAllWithImages().stream()
                        .map(l -> new MapListing(l.getId(), l.getPropertyType(), l.getLatitude(), l.getLongitude(),
                                l.getArea().getLabel(), l.getMonthlyRent(), l.getFreeSpots(),
                                ListingService.imageUrl(l.getImages().getFirst())))
                        .toList(),
                dorms.findAll(Sort.by("id")).stream()
                        .map(d -> new MapDorm(d.getId(), d.getName(), d.getLatitude(), d.getLongitude(),
                                seekers.getOrDefault(d.getId(), 0L),
                                d.getBlocks().stream().map(b -> toBlock(b, seekersByOption)).toList()))
                        .toList());
    }

    private static MapBlock toBlock(DormBlock block, Map<Long, Long> seekersByOption) {
        DormRoomOption option = block.getRoomOption();
        return new MapBlock(block.getName(), block.getLatitude(), block.getLongitude(),
                option == null ? null : option.getName(),
                option == null ? 0 : seekersByOption.getOrDefault(option.getId(), 0L));
    }

    private static Map<Long, Long> toCounts(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @GetMapping("/api/dorms/{id}")
    @Transactional(readOnly = true)
    public DormResponse dorm(@PathVariable Long id) {
        Dorm dorm = dorms.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Домот не постои."));
        List<DormSeeker> seekers = profiles.findByDormIdOrderByFirstName(id).stream()
                .map(MapController::toSeeker)
                .toList();
        return new DormResponse(dorm.getId(), dorm.getName(), seekers);
    }

    private static DormSeeker toSeeker(Profile profile) {
        DormRoomOption option = profile.getDormRoomOption();
        return new DormSeeker(ProfileService.toSummary(profile), option == null ? null : option.getName());
    }
}
