package mk.roommatematch.profile;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import mk.roommatematch.common.FieldValidationException;
import mk.roommatematch.listing.ImageStorage;
import mk.roommatematch.profile.ProfileDtos.ProfileRequest;
import mk.roommatematch.profile.ProfileDtos.ProfileResponse;
import mk.roommatematch.profile.ProfileDtos.ProfileSummary;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormRepository;
import mk.roommatematch.reference.DormRoomOption;
import mk.roommatematch.reference.Hobby;
import mk.roommatematch.reference.HobbyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private static final int MINIMUM_AGE = 18;

    private final ProfileRepository profiles;
    private final DormRepository dorms;
    private final HobbyRepository hobbies;
    private final ImageStorage images;

    public ProfileService(ProfileRepository profiles, DormRepository dorms, HobbyRepository hobbies,
                          ImageStorage images) {
        this.profiles = profiles;
        this.dorms = dorms;
        this.hobbies = hobbies;
        this.images = images;
    }

    /** Replaces the picture; the old file is removed so it does not stay on disk. */
    @Transactional
    public ProfileResponse setAvatar(Long userId, MultipartFile file) {
        Profile profile = existing(userId);
        String old = profile.getAvatar();
        profile.setAvatar(images.store(file));
        if (old != null) {
            images.delete(old);
        }
        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse removeAvatar(Long userId) {
        Profile profile = existing(userId);
        if (profile.getAvatar() != null) {
            images.delete(profile.getAvatar());
            profile.setAvatar(null);
        }
        return toResponse(profile);
    }

    private Profile existing(Long userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Профилот сè уште не е пополнет."));
    }

    public static String avatarUrl(Profile profile) {
        return profile.getAvatar() == null ? null : "/api/images/" + profile.getAvatar();
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
        DormRoomOption roomOption = null;
        if (request.housingType() == HousingType.DORM) {
            dorm = findDorm(request.dormId(), errors);
            roomOption = dorm == null ? null : findRoomOption(dorm, request.dormRoomOptionId(), errors);
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
        profile.update(request, dorm, roomOption, chosenHobbies);
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

    /** Only some dorms have room types to choose from. */
    private DormRoomOption findRoomOption(Dorm dorm, Long optionId, Map<String, String> errors) {
        if (dorm.getRoomOptions().isEmpty()) {
            return null;
        }
        if (optionId == null) {
            errors.put("dormRoomOptionId", "Изберете тип на соба.");
            return null;
        }
        return dorm.getRoomOptions().stream()
                .filter(option -> option.getId().equals(optionId))
                .findFirst()
                .orElseGet(() -> {
                    errors.put("dormRoomOptionId", "Избраниот тип на соба не постои во овој дом.");
                    return null;
                });
    }

    private void validateApartment(ProfileRequest request, Map<String, String> errors) {
        if (request.propertyTypes() == null || request.propertyTypes().isEmpty()) {
            errors.put("propertyTypes", "Изберете стан, куќа или и двете.");
        }
        if (request.situation() == null) {
            errors.put("situation", "Изберете ја вашата положба.");
        }
        // Someone who has a place gives neither: the rent and the location come from the listing.
        if (request.situation() != Situation.HAS_PLACE) {
            validateSearch(request, errors);
        }
        if (request.smoker() == null) {
            errors.put("smoker", "Одговорете дали пушите.");
        }
        if (request.hasPets() == null) {
            errors.put("hasPets", "Одговорете дали имате миленичиња.");
        }
        if (request.acceptsSmoker() == null) {
            errors.put("acceptsSmoker", "Одговорете дали прифаќате цимер пушач.");
        }
        if (request.acceptsPets() == null) {
            errors.put("acceptsPets", "Одговорете дали прифаќате цимер со миленичиња.");
        }
    }

    private static void validateSearch(ProfileRequest request, Map<String, String> errors) {
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
            errors.put("areas", "Изберете барем една локација.");
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
                p.getDormRoomOption() == null ? null : p.getDormRoomOption().getId(),
                p.getBudgetMin(),
                p.getBudgetMax(),
                Set.copyOf(p.getAreas()),
                Set.copyOf(p.getPropertyTypes()),
                inApartment(p) ? p.isSmoker() : null,
                inApartment(p) ? p.isHasPets() : null,
                p.getGuestsFrequency(),
                p.getCleaning(),
                p.getSleepSchedule(),
                p.getSound(),
                p.getCommunication(),
                p.getSharing(),
                p.getParties(),
                p.getPartnerStays(),
                inApartment(p) ? p.isAcceptsSmoker() : null,
                inApartment(p) ? p.isAcceptsPets() : null,
                p.getRoommateGenderPreference(),
                p.getHobbies().stream().map(Hobby::getId).collect(Collectors.toSet()),
                avatarUrl(p));
    }

    public static ProfileSummary toSummary(Profile p) {
        return new ProfileSummary(
                p.getUserId(),
                p.getFirstName(),
                p.getGender(),
                ageOf(p.getBirthDate()),
                p.getFaculty(),
                p.getBio(),
                inApartment(p) ? p.isSmoker() : null,
                inApartment(p) ? p.isHasPets() : null,
                p.getGuestsFrequency(),
                p.getCleaning(),
                p.getSleepSchedule(),
                p.getSound(),
                p.getCommunication(),
                p.getSharing(),
                p.getParties(),
                p.getPartnerStays(),
                p.getHobbies().stream().map(Hobby::getName).collect(Collectors.toCollection(TreeSet::new)),
                avatarUrl(p));
    }

    /** Smoking and pets are not allowed in dorms, so for dorm residents they are not shown at all. */
    private static boolean inApartment(Profile p) {
        return p.getHousingType() == HousingType.APARTMENT;
    }

    private static int ageOf(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
