package mk.roommatematch.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import mk.roommatematch.ai.MapFilters.PlaceKind;
import mk.roommatematch.listing.Listing;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.listing.ListingRepository;
import mk.roommatematch.profile.Profile;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileRepository;
import mk.roommatematch.reference.Area;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies the filters to the data. The model only reads the query; it never decides what is shown. */
@Service
public class MapFilterService {

    record Results(List<Long> listingIds, List<Long> dormIds) {
    }

    private final ListingRepository listings;
    private final DormRepository dorms;
    private final ProfileRepository profiles;

    public MapFilterService(ListingRepository listings, DormRepository dorms, ProfileRepository profiles) {
        this.listings = listings;
        this.dorms = dorms;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Set<String> dormNames() {
        return Set.copyOf(dorms.findAll().stream().map(Dorm::getName).toList());
    }

    @Transactional(readOnly = true)
    Results apply(MapFilters filters) {
        List<Long> listingIds = showsListings(filters)
                ? listings.findAllWithImages().stream().filter(l -> matches(l, filters)).map(Listing::getId).sorted().toList()
                : List.of();
        List<Long> dormIds = showsDorms(filters)
                ? dorms.findAll(Sort.by("id")).stream().filter(d -> matches(d, filters)).map(Dorm::getId).toList()
                : List.of();
        return new Results(listingIds, dormIds);
    }

    private static boolean showsListings(MapFilters filters) {
        if (!filters.dorms().isEmpty()) {
            return false;
        }
        return filters.kinds().isEmpty() || filters.kinds().contains(PlaceKind.APARTMENT)
                || filters.kinds().contains(PlaceKind.HOUSE);
    }

    /** Dorms have no rent or listing to compare, so they appear only when the search asks for one. */
    private static boolean showsDorms(MapFilters filters) {
        return !filters.dorms().isEmpty() || filters.kinds().contains(PlaceKind.DORM);
    }

    private boolean matches(Listing listing, MapFilters filters) {
        if (!filters.kinds().isEmpty() && !filters.kinds().contains(kindOf(listing.getPropertyType()))) {
            return false;
        }
        if (filters.hasLocation() && !inArea(listing.getArea(), filters)) {
            return false;
        }
        if (filters.minRent() != null && listing.getMonthlyRent() < filters.minRent()) {
            return false;
        }
        if (filters.maxRent() != null && listing.getMonthlyRent() > filters.maxRent()) {
            return false;
        }
        List<Profile> residents = profiles.findAllById(listing.getResidentIds());
        Gender gender = filters.residentGender();
        return residents.stream().allMatch(resident ->
                (!filters.noSmokers() || !resident.isSmoker())
                        && (!filters.noPets() || !resident.isHasPets())
                        && (gender == null || resident.getGender() == gender));
    }

    private static boolean matches(Dorm dorm, MapFilters filters) {
        if (!filters.dorms().isEmpty()) {
            return filters.dorms().contains(dorm.getName());
        }
        if (!filters.hasLocation()) {
            return true;
        }
        return dorm.getLatitude() != null && dorm.getLongitude() != null && inArea(nearestArea(dorm), filters);
    }

    private static boolean inArea(Area area, MapFilters filters) {
        return filters.areas().contains(area) || (filters.wholeOfSkopje() && area.isSkopje());
    }

    private static PlaceKind kindOf(PropertyType type) {
        return type == PropertyType.HOUSE ? PlaceKind.HOUSE : PlaceKind.APARTMENT;
    }

    /** Dorms store coordinates only, and the closest town or municipality is where they are. */
    private static Area nearestArea(Dorm dorm) {
        return Arrays.stream(Area.values())
                .min(Comparator.comparingDouble(area -> squaredDistance(area, dorm)))
                .orElseThrow();
    }

    private static double squaredDistance(Area area, Dorm dorm) {
        double latitude = area.getLatitude() - dorm.getLatitude();
        double longitude = (area.getLongitude() - dorm.getLongitude()) * Math.cos(Math.toRadians(dorm.getLatitude()));
        return latitude * latitude + longitude * longitude;
    }

    /** The filters in words, so the user sees how the search was understood. */
    static List<String> describe(MapFilters filters) {
        List<String> labels = new ArrayList<>();
        filters.kinds().forEach(kind -> labels.add(switch (kind) {
            case APARTMENT -> "Стан";
            case HOUSE -> "Куќа";
            case DORM -> "Студентски дом";
        }));
        if (filters.wholeOfSkopje()) {
            labels.add("Скопје");
        }
        filters.areas().forEach(area -> labels.add(area.getLabel()));
        labels.addAll(filters.dorms());
        if (filters.minRent() != null) {
            labels.add("Кирија од " + filters.minRent() + " €");
        }
        if (filters.maxRent() != null) {
            labels.add("Кирија до " + filters.maxRent() + " €");
        }
        if (filters.noSmokers()) {
            labels.add("Без пушачи");
        }
        if (filters.noPets()) {
            labels.add("Без миленичиња");
        }
        if (filters.residentGender() != null) {
            labels.add(filters.residentGender() == Gender.FEMALE ? "Само жени" : "Само мажи");
        }
        return labels;
    }
}
