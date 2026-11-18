package mk.roommatematch.distance;

import java.util.List;
import mk.roommatematch.distance.AddressSearch.Suggestion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Address suggestions for the listing form. Logged in only, so the shared search service is not open to anyone. */
@RestController
public class AddressController {

    private final AddressSearch search;

    public AddressController(AddressSearch search) {
        this.search = search;
    }

    @GetMapping("/api/addresses")
    List<Suggestion> addresses(@RequestParam(name = "q", defaultValue = "") String query) {
        return search.search(query.length() > 120 ? query.substring(0, 120) : query);
    }
}
