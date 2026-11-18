package mk.roommatematch.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import mk.roommatematch.common.FieldValidationException;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** An ad for a roommate in a dorm. Anyone with a profile may post one, for the dorm they choose. */
@RestController
@RequestMapping("/api/dorm-posts/mine")
public class DormPostController {

    /** Without a dorm, the one on the student's profile is used. */
    record DormPostRequest(
            Long dormId,

            @NotBlank(message = "Напиши нешто за себе и за цимерот што го бараш.")
            @Size(max = 1000, message = "Огласот може да има најмногу 1000 знаци.")
            String text) {
    }

    record DormPostResponse(String text, Long dormId, String dorm, Instant updatedAt) {
    }

    private final DormPostRepository posts;
    private final ProfileRepository profiles;
    private final DormRepository dorms;

    public DormPostController(DormPostRepository posts, ProfileRepository profiles, DormRepository dorms) {
        this.posts = posts;
        this.profiles = profiles;
        this.dorms = dorms;
    }

    @GetMapping
    @Transactional(readOnly = true)
    DormPostResponse get(@AuthenticationPrincipal Jwt jwt) {
        DormPost post = posts.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Немаш оглас."));
        return toResponse(post);
    }

    @PutMapping
    @Transactional
    DormPostResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DormPostRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        Profile profile = profiles.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Прво пополни го профилот."));
        Long dormId = request.dormId() != null ? request.dormId()
                : profile.getDorm() != null ? profile.getDorm().getId() : null;
        if (dormId == null || !dorms.existsById(dormId)) {
            throw new FieldValidationException(Map.of("dormId", "Избери студентски дом."));
        }
        DormPost post = posts.findById(userId).orElseGet(() -> new DormPost(userId));
        post.write(dormId, request.text().trim());
        return toResponse(posts.save(post));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete(@AuthenticationPrincipal Jwt jwt) {
        posts.deleteById(Long.valueOf(jwt.getSubject()));
    }

    private DormPostResponse toResponse(DormPost post) {
        Dorm dorm = dorms.findById(post.getDormId()).orElseThrow();
        return new DormPostResponse(post.getText(), dorm.getId(), dorm.getName(), post.getUpdatedAt());
    }
}
