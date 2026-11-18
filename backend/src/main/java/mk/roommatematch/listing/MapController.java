package mk.roommatematch.listing;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mk.roommatematch.listing.ListingDtos.AmenityResponse;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.profile.DormPost;
import mk.roommatematch.profile.DormPostRepository;
import mk.roommatematch.profile.Profile;
import mk.roommatematch.profile.ProfileDtos.ProfileSummary;
import mk.roommatematch.profile.ProfileRepository;
import mk.roommatematch.profile.ProfileService;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormBlock;
import mk.roommatematch.reference.DormPhotos;
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

    /**
     * A dorm with blocks is drawn as one point per block instead of a single point. The opening note is
     * set only for a dorm that is not in use yet.
     */
    record MapDorm(Long id, String name, Double latitude, Double longitude, long seekers, List<MapBlock> blocks,
                   String coverPhotoUrl, String openingNote) {
    }

    record MapResponse(List<MapListing> listings, List<MapDorm> dorms) {
    }

    /** The post is the student's own ad, or null when they have not written one. */
    record DormSeeker(ProfileSummary profile, String roomOption, String post) {
    }

    record DormResponse(Long id, String name, String neighbourhood, List<String> photoUrls, String openingNote,
                        List<DormSeeker> seekers) {
    }

    /** One card on the page of all listings, with what the place offers. */
    record BrowseListing(Long id, PropertyType propertyType, String area, String areaName, String address,
                         int monthlyRent, int freeSpots, List<AmenityResponse> amenities, String coverImageUrl,
                         int imageCount, int residents) {
    }

    /** A dorm has no rent or amenities listed; it has its own photos and may not be open yet. */
    record BrowseDorm(Long id, String name, Double latitude, Double longitude, int seekers, String coverPhotoUrl,
                      int photoCount, String openingNote, String neighbourhood) {
    }

    record BrowseResponse(List<BrowseListing> listings, List<BrowseDorm> dorms) {
    }

    private final ListingRepository listings;
    private final DormRepository dorms;
    private final ProfileRepository profiles;
    private final DormPostRepository dormPosts;
    private final DormPhotos dormPhotos;

    public MapController(ListingRepository listings, DormRepository dorms, ProfileRepository profiles,
                         DormPostRepository dormPosts, DormPhotos dormPhotos) {
        this.listings = listings;
        this.dorms = dorms;
        this.profiles = profiles;
        this.dormPosts = dormPosts;
        this.dormPhotos = dormPhotos;
    }

    private String cover(Dorm dorm) {
        List<String> photos = dormPhotos.urls(dorm.getSlug());
        return photos.isEmpty() ? null : photos.getFirst();
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
                                d.getBlocks().stream().map(b -> toBlock(b, seekersByOption)).toList(),
                                cover(d), d.openingNote(LocalDate.now())))
                        .toList());
    }

    /** Everything that can be browsed as a list: flats and houses with a photo, and every dorm. */
    @GetMapping("/api/browse")
    @Transactional(readOnly = true)
    public BrowseResponse browse() {
        return new BrowseResponse(
                listings.findAllWithImages().stream()
                        .sorted(Comparator.comparing(Listing::getId).reversed())
                        .map(l -> new BrowseListing(l.getId(), l.getPropertyType(), l.getArea().name(),
                                l.getArea().getLabel(), l.getAddress(), l.getMonthlyRent(), l.getFreeSpots(),
                                ListingDtos.amenities(l.getAmenities()),
                                ListingService.imageUrl(l.getImages().getFirst()),
                                l.getImages().size(), l.getResidentIds().size()))
                        .toList(),
                dorms.findAll(Sort.by("id")).stream()
                        .map(d -> {
                            List<Profile> seekers = profiles.findByDormIdOrderByFirstName(d.getId());
                            Double latitude = d.getLatitude();
                            Double longitude = d.getLongitude();
                            if (latitude == null && !d.getBlocks().isEmpty()) {
                                latitude = d.getBlocks().getFirst().getLatitude();
                                longitude = d.getBlocks().getFirst().getLongitude();
                            }
                            return new BrowseDorm(d.getId(), d.getName(), latitude, longitude, seekers.size(),
                                    cover(d), dormPhotos.urls(d.getSlug()).size(), d.openingNote(LocalDate.now()),
                                    d.getNeighbourhood());
                        })
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
        // Students whose profile names this dorm, and anyone who posted an ad for it.
        Map<Long, String> posts = dormPosts.findByDormId(id).stream()
                .collect(Collectors.toMap(DormPost::getUserId, DormPost::getText));
        Map<Long, Profile> byUser = new java.util.LinkedHashMap<>();
        profiles.findByDormIdOrderByFirstName(id).forEach(p -> byUser.put(p.getUserId(), p));
        profiles.findAllById(posts.keySet()).forEach(p -> byUser.putIfAbsent(p.getUserId(), p));
        List<Profile> people = List.copyOf(byUser.values());
        // Students with an ad come first, since they are actively looking.
        List<DormSeeker> seekers = people.stream()
                .map(profile -> toSeeker(profile, posts.get(profile.getUserId())))
                .sorted(Comparator.comparing((DormSeeker seeker) -> seeker.post() == null))
                .toList();
        return new DormResponse(dorm.getId(), dorm.getName(), dorm.getNeighbourhood(), dormPhotos.urls(dorm.getSlug()),
                dorm.openingNote(LocalDate.now()), seekers);
    }

    private static DormSeeker toSeeker(Profile profile, String post) {
        DormRoomOption option = profile.getDormRoomOption();
        return new DormSeeker(ProfileService.toSummary(profile), option == null ? null : option.getName(), post);
    }
}
