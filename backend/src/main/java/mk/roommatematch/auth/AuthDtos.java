package mk.roommatematch.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Внесете е-пошта.")
            @Email(message = "Внесете важечка е-пошта.")
            @Size(max = 254, message = "Е-поштата е предолга.")
            String email,

            @NotBlank(message = "Внесете лозинка.")
            @Size(min = 8, max = 72, message = "Лозинката мора да има од 8 до 72 знаци.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Внесете е-пошта.") String email,
            @NotBlank(message = "Внесете лозинка.") String password) {
    }

    /** For asking again for a confirmation link, or for a link to set a new password. */
    public record EmailRequest(
            @NotBlank(message = "Внесете е-пошта.")
            @Email(message = "Внесете важечка е-пошта.")
            @Size(max = 254, message = "Е-поштата е предолга.")
            String email) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,

            @NotBlank(message = "Внесете лозинка.")
            @Size(min = 8, max = 72, message = "Лозинката мора да има од 8 до 72 знаци.")
            String password) {
    }

    public record RegisterResponse(String email) {
    }

    public record AuthResponse(String token) {
    }

    public record CurrentUserResponse(Long id, String email, boolean profileCompleted) {
    }

    public record DeleteAccountRequest(@NotBlank(message = "Внесете лозинка.") String password) {
    }
}
