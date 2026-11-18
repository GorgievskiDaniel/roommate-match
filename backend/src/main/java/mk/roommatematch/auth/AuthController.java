package mk.roommatematch.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import mk.roommatematch.auth.AuthDtos.AuthResponse;
import mk.roommatematch.auth.AuthDtos.CurrentUserResponse;
import mk.roommatematch.auth.AuthDtos.DeleteAccountRequest;
import mk.roommatematch.auth.AuthDtos.EmailRequest;
import mk.roommatematch.auth.AuthDtos.LoginRequest;
import mk.roommatematch.auth.AuthDtos.RegisterRequest;
import mk.roommatematch.auth.AuthDtos.RegisterResponse;
import mk.roommatematch.auth.AuthDtos.ResetPasswordRequest;
import mk.roommatematch.auth.AuthDtos.VerifyEmailRequest;
import mk.roommatematch.config.SessionCookie;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final AccountDeletionService accountDeletion;
    private final SessionCookie cookie;

    public AuthController(AuthService authService, AccountDeletionService accountDeletion, SessionCookie cookie) {
        this.authService = authService;
        this.accountDeletion = accountDeletion;
        this.cookie = cookie;
    }

    /** The browser gets the token as an HttpOnly cookie; the body keeps it for other clients. */
    private AuthResponse signedIn(AuthResponse response, HttpServletResponse http) {
        cookie.issue(response.token(), http);
        return response;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/auth/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse http) {
        return signedIn(authService.login(request), http);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal Jwt jwt, HttpServletResponse http) {
        authService.logout(Long.valueOf(jwt.getSubject()));
        cookie.clear(http);
    }

    @PostMapping("/auth/resend-confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resendConfirmation(@Valid @RequestBody EmailRequest request) {
        authService.resendConfirmation(request.email());
    }

    @PostMapping("/auth/verify-email")
    AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletResponse http) {
        return signedIn(authService.verifyEmail(request.token()), http);
    }

    @PostMapping("/auth/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void forgotPassword(@Valid @RequestBody EmailRequest request) {
        authService.requestPasswordReset(request.email());
    }

    @PostMapping("/auth/reset-password")
    AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletResponse http) {
        return signedIn(authService.resetPassword(request), http);
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request,
                       HttpServletResponse http) {
        accountDeletion.delete(Long.valueOf(jwt.getSubject()), request.password());
        cookie.clear(http);
    }
}
