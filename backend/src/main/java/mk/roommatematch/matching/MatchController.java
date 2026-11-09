package mk.roommatematch.matching;

import java.util.List;
import mk.roommatematch.matching.ExplanationService.ExplanationResponse;
import mk.roommatematch.matching.MatchService.MatchResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchController {

    private final MatchService service;
    private final ExplanationService explanations;

    public MatchController(MatchService service, ExplanationService explanations) {
        this.service = service;
        this.explanations = explanations;
    }

    @GetMapping("/api/matches")
    List<MatchResponse> matches(@AuthenticationPrincipal Jwt jwt) {
        return service.matchesFor(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/api/matches/{otherId}/explanation")
    ExplanationResponse explanation(@PathVariable Long otherId, @AuthenticationPrincipal Jwt jwt) {
        return explanations.explain(Long.valueOf(jwt.getSubject()), otherId);
    }
}
