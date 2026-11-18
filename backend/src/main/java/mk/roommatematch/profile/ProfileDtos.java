package mk.roommatematch.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.profile.ProfileEnums.CleaningStyle;
import mk.roommatematch.profile.ProfileEnums.CommunicationLevel;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileEnums.GenderPreference;
import mk.roommatematch.profile.ProfileEnums.GuestsFrequency;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.PartnerStays;
import mk.roommatematch.profile.ProfileEnums.PartyAttitude;
import mk.roommatematch.profile.ProfileEnums.SharingStyle;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.profile.ProfileEnums.SleepSchedule;
import mk.roommatematch.profile.ProfileEnums.SoundLevel;
import mk.roommatematch.reference.Area;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileRequest(
            @NotBlank(message = "Внесете име.")
            @Size(max = 60, message = "Името е предолго.")
            String firstName,

            @NotNull(message = "Изберете пол.")
            Gender gender,

            @NotNull(message = "Внесете датум на раѓање.")
            @Past(message = "Датумот на раѓање мора да е во минатото.")
            LocalDate birthDate,

            @NotBlank(message = "Внесете факултет.")
            @Size(max = 120, message = "Името на факултетот е предолго.")
            String faculty,

            @Size(max = 500, message = "Описот може да има најмногу 500 знаци.")
            String bio,

            @NotNull(message = "Изберете каде барате цимер.")
            HousingType housingType,

            Situation situation,
            Long dormId,
            Long dormRoomOptionId,

            @Min(value = 0, message = "Буџетот не може да е негативен.")
            Integer budgetMin,

            @Min(value = 0, message = "Буџетот не може да е негативен.")
            Integer budgetMax,

            Set<Area> areas,
            Set<PropertyType> propertyTypes,

            /** Required for a flat or house only: dorms do not allow smoking or pets. */
            Boolean smoker,

            Boolean hasPets,

            @NotNull(message = "Изберете колку често викате гости.")
            GuestsFrequency guestsFrequency,

            @NotNull(message = "Изберете како би го организирале чистењето.")
            CleaningStyle cleaning,

            @NotNull(message = "Изберете распоред на спиење.")
            SleepSchedule sleepSchedule,

            @NotNull(message = "Изберете за музиката и звукот во станот.")
            SoundLevel sound,

            @NotNull(message = "Изберете колку сакате да комуницирате со цимерот.")
            CommunicationLevel communication,

            @NotNull(message = "Изберете како замислувате делење на работите.")
            SharingStyle sharing,

            @NotNull(message = "Изберете што мислите за забави дома.")
            PartyAttitude parties,

            @NotNull(message = "Изберете дали партнер може да преноќува.")
            PartnerStays partnerStays,

            /** Required for a flat or house only, like smoker and hasPets. */
            Boolean acceptsSmoker,

            Boolean acceptsPets,

            @NotNull(message = "Изберете пол на цимерот.")
            GenderPreference roommateGenderPreference,

            @NotNull(message = "Изберете барем едно хоби.")
            @Size(min = 1, max = 16, message = "Изберете барем едно хоби.")
            Set<Long> hobbyIds) {
    }

    public record ProfileResponse(
            String firstName,
            Gender gender,
            LocalDate birthDate,
            int age,
            String faculty,
            String bio,
            HousingType housingType,
            Situation situation,
            Long dormId,
            Long dormRoomOptionId,
            Integer budgetMin,
            Integer budgetMax,
            Set<Area> areas,
            Set<PropertyType> propertyTypes,
            /** These four are null for a dorm, where smoking and pets are not allowed. */
            Boolean smoker,
            Boolean hasPets,
            GuestsFrequency guestsFrequency,
            CleaningStyle cleaning,
            SleepSchedule sleepSchedule,
            SoundLevel sound,
            CommunicationLevel communication,
            SharingStyle sharing,
            PartyAttitude parties,
            PartnerStays partnerStays,
            Boolean acceptsSmoker,
            Boolean acceptsPets,
            GenderPreference roommateGenderPreference,
            Set<Long> hobbyIds,
            /** Null when the student has not added a picture; the page then draws one by gender. */
            String avatarUrl) {
    }

    /** What other students may see about someone: no e-mail and no exact birth date. */
    public record ProfileSummary(
            Long userId,
            String firstName,
            Gender gender,
            int age,
            String faculty,
            String bio,
            /** Null for a dorm, where smoking and pets are not allowed. */
            Boolean smoker,
            Boolean hasPets,
            GuestsFrequency guestsFrequency,
            CleaningStyle cleaning,
            SleepSchedule sleepSchedule,
            SoundLevel sound,
            CommunicationLevel communication,
            SharingStyle sharing,
            PartyAttitude parties,
            PartnerStays partnerStays,
            Set<String> hobbies,
            String avatarUrl) {
    }
}
