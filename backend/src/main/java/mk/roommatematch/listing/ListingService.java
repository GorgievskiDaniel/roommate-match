package mk.roommatematch.listing;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mk.roommatematch.common.FieldValidationException;
import mk.roommatematch.listing.ListingDtos.ImageResponse;
import mk.roommatematch.listing.ListingDtos.ListingRequest;
import mk.roommatematch.listing.ListingDtos.ListingResponse;
import mk.roommatematch.profile.Profile;
import mk.roommatematch.profile.ProfileDtos.ProfileSummary;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.profile.ProfileRepository;
import mk.roommatematch.profile.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListingService {

    private static final int MAX_IMAGES = 10;
    private static final int CODE_LENGTH = 8;
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final ListingRepository listings;
    private final ProfileRepository profiles;
    private final ImageStorage storage;
    private final SecureRandom random = new SecureRandom();

    public ListingService(ListingRepository listings, ProfileRepository profiles, ImageStorage storage) {
        this.listings = listings;
        this.profiles = profiles;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public ListingResponse mine(Long userId) {
        Listing listing = listings.findByResident(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Немате оглас."));
        return toResponse(listing, userId);
    }

    /** A listing without photos stays hidden from everyone but its residents. */
    @Transactional(readOnly = true)
    public ListingResponse get(Long id, Long userId) {
        Listing listing = find(id);
        if (listing.getImages().isEmpty() && !listing.getResidentIds().contains(userId)) {
            throw notFound();
        }
        return toResponse(listing, userId);
    }

    @Transactional
    public ListingResponse create(Long userId, ListingRequest request) {
        requireHasPlace(userId);
        requireNotResident(userId);
        Listing listing = new Listing(userId, newInviteCode());
        listing.update(request);
        return toResponse(listings.save(listing), userId);
    }

    @Transactional
    public ListingResponse update(Long id, Long userId, ListingRequest request) {
        Listing listing = findOwned(id, userId);
        listing.update(request);
        return toResponse(listing, userId);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Listing listing = findOwned(id, userId);
        List<String> files = listing.getImages().stream().map(ListingImage::getFileName).toList();
        listings.delete(listing);
        listings.flush();
        files.forEach(storage::delete);
    }

    @Transactional
    public ListingResponse join(Long userId, String code) {
        requireHasPlace(userId);
        requireNotResident(userId);
        Listing listing = listings.findByInviteCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new FieldValidationException(Map.of("code", "Не постои оглас со овој код.")));
        listing.getResidentIds().add(userId);
        return toResponse(listing, userId);
    }

    @Transactional
    public void leave(Long id, Long userId) {
        Listing listing = find(id);
        if (listing.getOwnerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сопственикот не може да го напушти огласот, може само да го избрише.");
        }
        if (!listing.getResidentIds().remove(userId)) {
            throw notFound();
        }
    }

    @Transactional
    public ListingResponse removeResident(Long id, Long userId, Long residentId) {
        Listing listing = findOwned(id, userId);
        if (residentId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сопственикот не може да се отстрани себеси.");
        }
        listing.getResidentIds().remove(residentId);
        return toResponse(listing, userId);
    }

    @Transactional
    public ListingResponse addImages(Long id, Long userId, List<MultipartFile> files) {
        Listing listing = findOwned(id, userId);
        if (files.isEmpty()) {
            throw new FieldValidationException(Map.of("images", "Изберете барем една слика."));
        }
        if (listing.getImages().size() + files.size() > MAX_IMAGES) {
            throw new FieldValidationException(Map.of("images", "Огласот може да има најмногу " + MAX_IMAGES + " слики."));
        }
        List<String> stored = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                stored.add(storage.store(file));
            }
        } catch (RuntimeException e) {
            stored.forEach(storage::delete);
            throw e;
        }
        stored.forEach(name -> listing.getImages().add(new ListingImage(name)));
        listings.flush();
        return toResponse(listing, userId);
    }

    @Transactional
    public ListingResponse deleteImage(Long id, Long userId, Long imageId) {
        Listing listing = findOwned(id, userId);
        ListingImage image = listing.getImages().stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(ListingService::notFound);
        if (listing.getImages().size() == 1) {
            throw new FieldValidationException(Map.of("images", "Огласот мора да има барем една слика. Прво прикачете нова."));
        }
        listing.getImages().remove(image);
        listings.flush();
        storage.delete(image.getFileName());
        return toResponse(listing, userId);
    }

    private ListingResponse toResponse(Listing l, Long viewerId) {
        boolean resident = l.getResidentIds().contains(viewerId);
        // The owner comes first, the others by name.
        List<ProfileSummary> residents = profiles.findAllById(l.getResidentIds()).stream()
                .sorted(Comparator.comparing((Profile p) -> !p.getUserId().equals(l.getOwnerId()))
                        .thenComparing(Profile::getFirstName))
                .map(ProfileService::toSummary)
                .toList();
        return new ListingResponse(
                l.getId(),
                l.getOwnerId(),
                l.getPropertyType(),
                l.getAddress(),
                l.getArea(),
                l.getArea().getLabel(),
                l.getLatitude(),
                l.getLongitude(),
                l.getMonthlyRent(),
                l.getFreeSpots(),
                l.getDescription(),
                resident ? l.getInviteCode() : null,
                l.getImages().stream().map(i -> new ImageResponse(i.getId(), imageUrl(i))).toList(),
                residents);
    }

    static String imageUrl(ListingImage image) {
        return "/api/images/" + image.getFileName();
    }

    private void requireHasPlace(Long userId) {
        Profile profile = profiles.findById(userId).orElse(null);
        if (profile == null || profile.getSituation() != Situation.HAS_PLACE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Огласи можат да прават само корисници што имаат стан.");
        }
    }

    private void requireNotResident(Long userId) {
        if (listings.findByResident(userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Веќе сте дел од оглас.");
        }
    }

    private Listing find(Long id) {
        return listings.findById(id).orElseThrow(ListingService::notFound);
    }

    private Listing findOwned(Long id, Long userId) {
        Listing listing = find(id);
        if (!listing.getOwnerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Само сопственикот може да го менува огласот.");
        }
        return listing;
    }

    private String newInviteCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Огласот не постои.");
    }
}
