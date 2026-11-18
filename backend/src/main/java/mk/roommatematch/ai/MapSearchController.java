package mk.roommatematch.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mk.roommatematch.ai.MapSearchService.SearchResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapSearchController {

    record SearchRequest(
            @NotBlank(message = "Напишете што барате.")
            @Size(max = 300, message = "Барањето може да има најмногу 300 знаци.")
            String query) {
    }

    private final MapSearchService service;

    public MapSearchController(MapSearchService service) {
        this.service = service;
    }

    @PostMapping("/api/map/search")
    SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return service.search(request.query().trim());
    }
}
