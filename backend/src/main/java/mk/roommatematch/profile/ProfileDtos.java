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
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileEnums.GenderPreference;
import mk.roommatematch.profile.ProfileEnums.GuestsFrequency;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.SleepSchedule;
import mk.roommatematch.profile.ProfileEnums.Situation;
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

            @NotNull(message = "Одговорете дали пушите.")
            Boolean smoker,

            @NotNull(message = "Одговорете дали имате миленичиња.")
            Boolean hasPets,

            @NotNull(message = "Изберете колку често викате гости.")
            GuestsFrequency guestsFrequency,

            @NotNull(message = "Оценете ја уреденоста.")
            @Min(value = 1, message = "Оценката е од 1 до 5.")
            @Max(value = 5, message = "Оценката е од 1 до 5.")
            Integer tidiness,

            @NotNull(message = "Изберете распоред на спиење.")
            SleepSchedule sleepSchedule,

            @NotNull(message = "Оценете ја потребата за тишина.")
            @Min(value = 1, message = "Оценката е од 1 до 5.")
            @Max(value = 5, message = "Оценката е од 1 до 5.")
            Integer quietNeed,

            @NotNull(message = "Одговорете дали прифаќате цимер пушач.")
            Boolean acceptsSmoker,

            @NotNull(message = "Одговорете дали прифаќате цимер со миленичиња.")
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
            boolean smoker,
            boolean hasPets,
            GuestsFrequency guestsFrequency,
            int tidiness,
            SleepSchedule sleepSchedule,
            int quietNeed,
            boolean acceptsSmoker,
            boolean acceptsPets,
            GenderPreference roommateGenderPreference,
            Set<Long> hobbyIds) {
    }

    /** What other students may see about someone: no e-mail and no exact birth date. */
    public record ProfileSummary(
            Long userId,
            String firstName,
            Gender gender,
            int age,
            String faculty,
            String bio,
            boolean smoker,
            boolean hasPets,
            GuestsFrequency guestsFrequency,
            int tidiness,
            SleepSchedule sleepSchedule,
            int quietNeed,
            Set<String> hobbies) {
    }
}
