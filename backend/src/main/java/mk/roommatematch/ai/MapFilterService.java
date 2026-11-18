package mk.roommatematch.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import mk.roommatematch.ai.MapFilters.PlaceKind;
import mk.roommatematch.distance.DistanceService;
import mk.roommatematch.distance.Faculty;
import mk.roommatematch.distance.FacultyRepository;
import mk.roommatematch.distance.RouteClient.Mode;
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

    /** How many of the closest by straight line are measured by road when only the nearest is wanted. */
    private static final int NEAREST_CANDIDATES = 3;

    private final ListingRepository listings;
    private final DormRepository dorms;
    private final ProfileRepository profiles;
    private final FacultyRepository faculties;
    private final DistanceService distances;

    public MapFilterService(ListingRepository listings, DormRepository dorms, ProfileRepository profiles,
                            FacultyRepository faculties, DistanceService distances) {
        this.listings = listings;
        this.dorms = dorms;
        this.profiles = profiles;
        this.faculties = faculties;
        this.distances = distances;
    }

    /** Faculties the search can measure from, by code. */
    @Transactional(readOnly = true)
    public Map<String, Faculty> faculties() {
        Map<String, Faculty> byCode = new LinkedHashMap<>();
        faculties.findAll(Sort.by("id")).forEach(faculty -> byCode.put(faculty.getCode(), faculty));
        return byCode;
    }

    @Transactional(readOnly = true)
    public Set<String> dormNames() {
        return Set.copyOf(dorms.findAll().stream().map(Dorm::getName).toList());
    }

    @Transactional(readOnly = true)
    Results apply(MapFilters filters) {
        List<Listing> foundListings = showsListings(filters)
                ? listings.findAllWithImages().stream().filter(l -> matches(l, filters)).toList()
                : List.of();
        List<Dorm> foundDorms = showsDorms(filters)
                ? dorms.findAll(Sort.by("id")).stream().filter(d -> matches(d, filters)).toList()
                : List.of();
        if (filters.measuresFromFaculty()) {
            foundListings = byDistance(foundListings, Listing::getLatitude, Listing::getLongitude, filters);
            // "The closest dorm" means one a student can move into now.
            java.time.LocalDate today = java.time.LocalDate.now();
            foundDorms = byDistance(
                    foundDorms.stream().filter(d -> d.getLatitude() != null && d.getLongitude() != null)
                            .filter(d -> !filters.nearest() || d.isOpen(today)).toList(),
                    Dorm::getLatitude, Dorm::getLongitude, filters);
        }
        return new Results(foundListings.stream().map(Listing::getId).sorted().toList(),
                foundDorms.stream().map(Dorm::getId).sorted().toList());
    }

    /**
     * Keeps the places within the limit, or only the closest one. The straight line, which is never
     * longer than the road, narrows the field first, so the routing service is asked only about the rest.
     */
    private <T> List<T> byDistance(List<T> places, ToDoubleFunction<T> latitude, ToDoubleFunction<T> longitude,
                                   MapFilters filters) {
        Faculty faculty = filters.faculty();
        ToDoubleFunction<T> straight = place ->
                DistanceService.straightLineKm(latitude.applyAsDouble(place), longitude.applyAsDouble(place), faculty);
        // Nobody walks faster than 6 km/h or drives through town faster than 90 km/h on average.
        Double reachableKm = filters.maxMinutes() == null ? null
                : filters.maxMinutes() / 60.0 * (filters.travelMode() == Mode.WALKING ? 6 : 90);
        Stream<T> candidates = places.stream()
                .filter(place -> filters.maxKm() == null || straight.applyAsDouble(place) <= filters.maxKm())
                .filter(place -> reachableKm == null || straight.applyAsDouble(place) <= reachableKm)
                .sorted(Comparator.comparingDouble(straight));
        if (filters.nearest()) {
            candidates = candidates.limit(NEAREST_CANDIDATES);
        }
        Map<T, Double> road = new LinkedHashMap<>();
        candidates
                .filter(place -> filters.maxMinutes() == null || distances.minutes(filters.travelMode(),
                        latitude.applyAsDouble(place), longitude.applyAsDouble(place), faculty) <= filters.maxMinutes())
                .forEach(place -> road.put(place,
                        distances.walkingKm(latitude.applyAsDouble(place), longitude.applyAsDouble(place), faculty)));
        Stream<Map.Entry<T, Double>> kept = road.entrySet().stream()
                .filter(entry -> filters.maxKm() == null || entry.getValue() <= filters.maxKm());
        if (filters.nearest()) {
            kept = kept.min(Map.Entry.comparingByValue()).stream();
        }
        return kept.map(Map.Entry::getKey).toList();
    }

    private static boolean showsListings(MapFilters filters) {
        if (!filters.dorms().isEmpty()) {
            return false;
        }
        return filters.kinds().isEmpty() || filters.kinds().contains(PlaceKind.APARTMENT)
                || filters.kinds().contains(PlaceKind.HOUSE);
    }

    /**
     * Dorms have no rent or residents to compare, so they appear when the search asks for one, or when it
     * only asks for a distance from a faculty, such as "најмногу 5 km од ФИНКИ", which a dorm can meet.
     */
    private static boolean showsDorms(MapFilters filters) {
        if (!filters.dorms().isEmpty() || filters.kinds().contains(PlaceKind.DORM)) {
            return true;
        }
        boolean asksAboutListingsOnly = filters.minRent() != null || filters.maxRent() != null
                || filters.noSmokers() || filters.noPets() || filters.residentGender() != null;
        return filters.kinds().isEmpty() && filters.measuresFromFaculty() && !asksAboutListingsOnly;
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
        // Dorms store coordinates only, and the closest town or municipality is where they are.
        return dorm.getLatitude() != null && dorm.getLongitude() != null
                && inArea(Area.nearestTo(dorm.getLatitude(), dorm.getLongitude()), filters);
    }

    private static boolean inArea(Area area, MapFilters filters) {
        return filters.areas().contains(area) || (filters.wholeOfSkopje() && area.isSkopje());
    }

    private static PlaceKind kindOf(PropertyType type) {
        return type == PropertyType.HOUSE ? PlaceKind.HOUSE : PlaceKind.APARTMENT;
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
        if (filters.measuresFromFaculty()) {
            String faculty = filters.faculty().getDisplayName();
            if (filters.maxKm() != null) {
                labels.add("До " + kilometres(filters.maxKm()) + " km од " + faculty);
            }
            if (filters.maxMinutes() != null) {
                labels.add("До " + filters.maxMinutes() + " мин. "
                        + (filters.travelMode() == Mode.WALKING ? "пешки" : "со кола") + " од " + faculty);
            }
            if (filters.nearest()) {
                labels.add("Најблиску до " + faculty);
            }
        }
        return labels;
    }

    private static String kilometres(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
