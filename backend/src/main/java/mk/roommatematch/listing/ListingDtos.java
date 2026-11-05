package mk.roommatematch.listing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import mk.roommatematch.profile.ProfileDtos.ProfileSummary;
import mk.roommatematch.reference.Area;

public final class ListingDtos {

    private ListingDtos() {
    }

    public enum PropertyType { APARTMENT, HOUSE }

    public record ListingRequest(
            @NotNull(message = "Изберете дали е стан или куќа.")
            PropertyType propertyType,

            @NotBlank(message = "Внесете адреса.")
            @Size(max = 200, message = "Адресата е предолга.")
            String address,

            @NotNull(message = "Изберете град или општина.")
            Area area,

            @NotNull(message = "Означете ја локацијата на мапата.")
            @DecimalMin(value = "40.8", message = "Локацијата мора да е во Македонија.")
            @DecimalMax(value = "42.4", message = "Локацијата мора да е во Македонија.")
            Double latitude,

            @NotNull(message = "Означете ја локацијата на мапата.")
            @DecimalMin(value = "20.4", message = "Локацијата мора да е во Македонија.")
            @DecimalMax(value = "23.1", message = "Локацијата мора да е во Македонија.")
            Double longitude,

            @NotNull(message = "Внесете кирија.")
            @Min(value = 0, message = "Киријата не може да е негативна.")
            @Max(value = 10000, message = "Проверете ја киријата.")
            Integer monthlyRent,

            @NotNull(message = "Внесете колку цимери барате.")
            @Min(value = 1, message = "Барате најмалку еден цимер.")
            @Max(value = 10, message = "Можете да барате најмногу 10 цимери.")
            Integer freeSpots,

            @NotBlank(message = "Опишете го станот.")
            @Size(max = 1000, message = "Описот може да има најмногу 1000 знаци.")
            String description) {
    }

    public record JoinRequest(@NotBlank(message = "Внесете код.") String code) {
    }

    public record ImageResponse(Long id, String url) {
    }

    public record ListingResponse(
            Long id,
            Long ownerId,
            PropertyType propertyType,
            String address,
            Area area,
            String areaName,
            double latitude,
            double longitude,
            int monthlyRent,
            int freeSpots,
            String description,
            /** Only residents get the code, so only they can invite someone. */
            String inviteCode,
            List<ImageResponse> images,
            List<ProfileSummary> residents) {
    }
}
