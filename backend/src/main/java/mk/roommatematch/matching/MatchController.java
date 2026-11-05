package mk.roommatematch.matching;

import java.util.List;
import mk.roommatematch.matching.MatchService.MatchResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchController {

    private final MatchService service;

    public MatchController(MatchService service) {
        this.service = service;
    }

    @GetMapping("/api/matches")
    List<MatchResponse> matches(@AuthenticationPrincipal Jwt jwt) {
        return service.matchesFor(Long.valueOf(jwt.getSubject()));
    }
}
