package mk.roommatematch.listing;

import jakarta.validation.Valid;
import java.util.List;
import mk.roommatematch.listing.ListingDtos.JoinRequest;
import mk.roommatematch.listing.ListingDtos.ListingRequest;
import mk.roommatematch.listing.ListingDtos.ListingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService service;

    public ListingController(ListingService service) {
        this.service = service;
    }

    @GetMapping("/mine")
    ListingResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(userId(jwt));
    }

    @GetMapping("/{id}")
    ListingResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, userId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ListingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ListingRequest request) {
        return service.create(userId(jwt), request);
    }

    @PutMapping("/{id}")
    ListingResponse update(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt,
                           @Valid @RequestBody ListingRequest request) {
        return service.update(id, userId(jwt), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(id, userId(jwt));
    }

    @PostMapping("/join")
    ListingResponse join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinRequest request) {
        return service.join(userId(jwt), request.code());
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.leave(id, userId(jwt));
    }

    @DeleteMapping("/{id}/residents/{residentId}")
    ListingResponse removeResident(@PathVariable Long id, @PathVariable Long residentId,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.removeResident(id, userId(jwt), residentId);
    }

    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ListingResponse addImages(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt,
                              @RequestPart("images") List<MultipartFile> images) {
        return service.addImages(id, userId(jwt), images);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    ListingResponse deleteImage(@PathVariable Long id, @PathVariable Long imageId,
                                @AuthenticationPrincipal Jwt jwt) {
        return service.deleteImage(id, userId(jwt), imageId);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
