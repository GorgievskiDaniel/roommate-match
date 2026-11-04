package mk.roommatematch.profile;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mk.roommatematch.common.FieldValidationException;
import mk.roommatematch.profile.ProfileDtos.ProfileRequest;
import mk.roommatematch.profile.ProfileDtos.ProfileResponse;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormRepository;
import mk.roommatematch.reference.Hobby;
import mk.roommatematch.reference.HobbyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private static final int MINIMUM_AGE = 18;

    private final ProfileRepository profiles;
    private final DormRepository dorms;
    private final HobbyRepository hobbies;

    public ProfileService(ProfileRepository profiles, DormRepository dorms, HobbyRepository hobbies) {
        this.profiles = profiles;
        this.dorms = dorms;
        this.hobbies = hobbies;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        return profiles.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Профилот сè уште не е пополнет."));
    }

    @Transactional
    public ProfileResponse save(Long userId, ProfileRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ageOf(request.birthDate()) < MINIMUM_AGE) {
            errors.put("birthDate", "Мора да имате најмалку " + MINIMUM_AGE + " години.");
        }
        Dorm dorm = null;
        if (request.housingType() == HousingType.DORM) {
            dorm = findDorm(request.dormId(), errors);
        } else {
            validateApartment(request, errors);
        }
        Set<Hobby> chosenHobbies = new HashSet<>(hobbies.findAllById(request.hobbyIds()));
        if (chosenHobbies.size() != request.hobbyIds().size()) {
            errors.put("hobbyIds", "Избрано е непостоечко хоби.");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        profile.update(request, dorm, chosenHobbies);
        return toResponse(profiles.save(profile));
    }

    private Dorm findDorm(Long dormId, Map<String, String> errors) {
        if (dormId == null) {
            errors.put("dormId", "Изберете студентски дом.");
            return null;
        }
        Dorm dorm = dorms.findById(dormId).orElse(null);
        if (dorm == null) {
            errors.put("dormId", "Избраниот дом не постои.");
        }
        return dorm;
    }

    private void validateApartment(ProfileRequest request, Map<String, String> errors) {
        if (request.situation() == null) {
            errors.put("situation", "Изберете ја вашата положба.");
        }
        if (request.budgetMin() == null) {
            errors.put("budgetMin", "Внесете најмал буџет.");
        }
        if (request.budgetMax() == null) {
            errors.put("budgetMax", "Внесете најголем буџет.");
        }
        if (request.budgetMin() != null && request.budgetMax() != null
                && request.budgetMin() > request.budgetMax()) {
            errors.put("budgetMax", "Најголемиот буџет не може да е помал од најмалиот.");
        }
        if (request.areas() == null || request.areas().isEmpty()) {
            errors.put("areas", "Изберете барем една општина.");
        }
    }

    private ProfileResponse toResponse(Profile p) {
        return new ProfileResponse(
                p.getFirstName(),
                p.getGender(),
                p.getBirthDate(),
                ageOf(p.getBirthDate()),
                p.getFaculty(),
                p.getBio(),
                p.getHousingType(),
                p.getSituation(),
                p.getDorm() == null ? null : p.getDorm().getId(),
                p.getBudgetMin(),
                p.getBudgetMax(),
                Set.copyOf(p.getAreas()),
                p.isSmoker(),
                p.isHasPets(),
                p.getGuestsFrequency(),
                p.getTidiness(),
                p.getSleepSchedule(),
                p.getQuietNeed(),
                p.isAcceptsSmoker(),
                p.isAcceptsPets(),
                p.getRoommateGenderPreference(),
                p.getHobbies().stream().map(Hobby::getId).collect(Collectors.toSet()));
    }

    private static int ageOf(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
