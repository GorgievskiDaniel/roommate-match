package mk.roommatematch.reference;

import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The fixed choices offered by the questionnaire. */
@RestController
public class ReferenceController {

    record Option(Long id, String name) {
    }

    record DormOption(Long id, String name, List<Option> roomOptions) {
    }

    record AreaOption(Area code, String name, boolean skopje, double latitude, double longitude) {
    }

    record ReferenceResponse(List<DormOption> dorms, List<Option> hobbies, List<AreaOption> areas) {
    }

    private final DormRepository dorms;
    private final HobbyRepository hobbies;

    public ReferenceController(DormRepository dorms, HobbyRepository hobbies) {
        this.dorms = dorms;
        this.hobbies = hobbies;
    }

    @GetMapping("/api/reference")
    ReferenceResponse reference() {
        return new ReferenceResponse(
                dorms.findAllWithRoomOptions().stream().map(ReferenceController::toOption).toList(),
                hobbies.findAll(Sort.by("id")).stream().map(h -> new Option(h.getId(), h.getName())).toList(),
                Arrays.stream(Area.values()).map(a -> new AreaOption(a, a.getLabel(), a.isSkopje(), a.getLatitude(), a.getLongitude())).toList());
    }

    private static DormOption toOption(Dorm dorm) {
        return new DormOption(dorm.getId(), dorm.getName(),
                dorm.getRoomOptions().stream().map(o -> new Option(o.getId(), o.getName())).toList());
    }
}
