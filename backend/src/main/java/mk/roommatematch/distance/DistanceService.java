package mk.roommatematch.distance;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mk.roommatematch.distance.RouteClient.Mode;
import mk.roommatematch.distance.RouteClient.Route;
import mk.roommatematch.listing.ListingDtos.ListingResponse;
import mk.roommatematch.listing.ListingService;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DistanceService {

    public record Trip(double kilometers, int minutes) {
    }

    /** Walking is null when the place is too far for it to be a real option. */
    public record DistanceResponse(String faculty, Trip walking, Trip driving) {
    }

    public record FacultyOption(Long id, String name, double latitude, double longitude) {
    }

    public record NearestDorm(String dorm, DistanceResponse distance) {
    }

    private static final double WALKABLE_KM = 15;
    private static final double EARTH_RADIUS_KM = 6371;
    private static final int NEAREST_CANDIDATES = 3;

    private final FacultyRepository faculties;
    private final DormRepository dorms;
    private final ListingService listings;
    private final RouteClient routes;

    public DistanceService(FacultyRepository faculties, DormRepository dorms, ListingService listings, RouteClient routes) {
        this.faculties = faculties;
        this.dorms = dorms;
        this.listings = listings;
        this.routes = routes;
    }

    public List<FacultyOption> options() {
        return faculties.findAll(Sort.by("id")).stream()
                .map(faculty -> new FacultyOption(faculty.getId(), faculty.getDisplayName(),
                        faculty.getLatitude(), faculty.getLongitude()))
                .toList();
    }

    /** Goes through the listing rules, so a hidden listing stays hidden here as well. */
    public DistanceResponse fromListing(Long listingId, Long userId, Long facultyId) {
        ListingResponse listing = listings.get(listingId, userId);
        return between(listing.latitude(), listing.longitude(), faculty(facultyId));
    }

    public DistanceResponse fromDorm(Long dormId, Long facultyId) {
        Dorm dorm = dorms.findById(dormId)
                .filter(found -> found.getLatitude() != null && found.getLongitude() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Домот не постои."));
        return fromDorm(dorm, faculty(facultyId));
    }

    DistanceResponse fromDorm(Dorm dorm, Faculty faculty) {
        return between(dorm.getLatitude(), dorm.getLongitude(), faculty);
    }

    /** Straight-line distance narrows the field, then real routes decide among the closest dorms. */
    public Optional<NearestDorm> nearestDorm(Faculty faculty) {
        // A dorm still being built is no answer to where a student can live now.
        java.time.LocalDate today = java.time.LocalDate.now();
        return dorms.findAll().stream()
                .filter(dorm -> dorm.isOpen(today))
                .filter(dorm -> dorm.getLatitude() != null && dorm.getLongitude() != null)
                .sorted(Comparator.comparingDouble(dorm -> straightLineKm(dorm.getLatitude(), dorm.getLongitude(), faculty)))
                .limit(NEAREST_CANDIDATES)
                .map(dorm -> new NearestDorm(dorm.getName(), fromDorm(dorm, faculty)))
                .min(Comparator.comparingInt(nearest -> nearest.distance().driving().minutes() * 1000
                        + (nearest.distance().walking() == null ? 999 : nearest.distance().walking().minutes())));
    }

    private DistanceResponse between(double latitude, double longitude, Faculty faculty) {
        boolean walkable = straightLineKm(latitude, longitude, faculty) <= WALKABLE_KM;
        Trip walking = walkable
                ? toTrip(routes.route(Mode.WALKING, latitude, longitude, faculty.getLatitude(), faculty.getLongitude()))
                : null;
        Trip driving = toTrip(routes.route(Mode.DRIVING, latitude, longitude, faculty.getLatitude(), faculty.getLongitude()));
        return new DistanceResponse(faculty.getDisplayName(), walking, driving);
    }

    private Faculty faculty(Long id) {
        return faculties.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Факултетот не постои."));
    }

    private static Trip toTrip(Route route) {
        return new Trip(Math.round(route.meters() / 100) / 10.0, Math.max(1, (int) Math.round(route.seconds() / 60)));
    }

    /**
     * The walking distance by road, for limits such as "до 5 km од ФИНКИ". When the routing service
     * does not answer, the straight line stands in, which is never longer than the road.
     */
    public double walkingKm(double latitude, double longitude, Faculty faculty) {
        try {
            return routes.route(Mode.WALKING, latitude, longitude, faculty.getLatitude(), faculty.getLongitude())
                    .meters() / 1000;
        } catch (RuntimeException e) {
            return straightLineKm(latitude, longitude, faculty);
        }
    }

    /**
     * Minutes on foot or by car. When the routing service does not answer, the time is estimated from
     * the straight line, a third longer for the streets, at 5 km/h on foot or 30 km/h in town by car.
     */
    public double minutes(Mode mode, double latitude, double longitude, Faculty faculty) {
        try {
            return routes.route(mode, latitude, longitude, faculty.getLatitude(), faculty.getLongitude()).seconds() / 60;
        } catch (RuntimeException e) {
            double km = straightLineKm(latitude, longitude, faculty) * 1.3;
            return km / (mode == Mode.WALKING ? 5 : 30) * 60;
        }
    }

    public static double straightLineKm(double latitude, double longitude, Faculty faculty) {
        double north = Math.toRadians(faculty.getLatitude() - latitude);
        double east = Math.toRadians(faculty.getLongitude() - longitude);
        double h = Math.pow(Math.sin(north / 2), 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(faculty.getLatitude()))
                * Math.pow(Math.sin(east / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
