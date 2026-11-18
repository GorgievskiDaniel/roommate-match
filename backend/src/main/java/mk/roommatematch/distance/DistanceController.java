package mk.roommatematch.distance;

import java.util.List;
import mk.roommatematch.distance.DistanceService.DistanceResponse;
import mk.roommatematch.distance.DistanceService.FacultyOption;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DistanceController {

    private final DistanceService service;

    public DistanceController(DistanceService service) {
        this.service = service;
    }

    @GetMapping("/api/faculties")
    List<FacultyOption> faculties() {
        return service.options();
    }

    @GetMapping("/api/listings/{id}/distance")
    DistanceResponse fromListing(@PathVariable Long id, @RequestParam Long facultyId, @AuthenticationPrincipal Jwt jwt) {
        return service.fromListing(id, Long.valueOf(jwt.getSubject()), facultyId);
    }

    @GetMapping("/api/dorms/{id}/distance")
    DistanceResponse fromDorm(@PathVariable Long id, @RequestParam Long facultyId) {
        return service.fromDorm(id, facultyId);
    }
}
