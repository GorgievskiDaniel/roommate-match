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

    /** The opening note is set only for a dorm that is not in use yet. */
    record DormOption(Long id, String name, List<Option> roomOptions, String openingNote) {
    }

    record AreaOption(Area code, String name, boolean skopje, double latitude, double longitude) {
    }

    /** The label is what gets saved; the rest only helps the search find it. */
    record FacultyOption(String label, String shortName, String latin, String university, String city) {
    }

    record ReferenceResponse(List<DormOption> dorms, List<Option> hobbies, List<AreaOption> areas,
                             List<FacultyOption> faculties) {
    }

    private final DormRepository dorms;
    private final HobbyRepository hobbies;
    private final StudyFacultyRepository faculties;

    public ReferenceController(DormRepository dorms, HobbyRepository hobbies, StudyFacultyRepository faculties) {
        this.dorms = dorms;
        this.hobbies = hobbies;
        this.faculties = faculties;
    }

    @GetMapping("/api/reference")
    ReferenceResponse reference() {
        return new ReferenceResponse(
                dorms.findAllWithRoomOptions().stream().map(ReferenceController::toOption).toList(),
                hobbies.findAll(Sort.by("id")).stream().map(h -> new Option(h.getId(), h.getName())).toList(),
                Arrays.stream(Area.values()).map(a -> new AreaOption(a, a.getLabel(), a.isSkopje(), a.getLatitude(), a.getLongitude())).toList(),
                faculties.findAll(Sort.by("position")).stream()
                        .map(f -> new FacultyOption(f.label(), f.getShortName(), f.getLatin(), f.getUniversity(), f.getCity()))
                        .toList());
    }

    private static DormOption toOption(Dorm dorm) {
        return new DormOption(dorm.getId(), dorm.getName(),
                dorm.getRoomOptions().stream().map(o -> new Option(o.getId(), o.getName())).toList(),
                dorm.openingNote(java.time.LocalDate.now()));
    }
}
