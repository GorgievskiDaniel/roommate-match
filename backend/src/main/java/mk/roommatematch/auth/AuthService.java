package mk.roommatematch.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import mk.roommatematch.auth.AccountToken.Purpose;
import mk.roommatematch.auth.AuthDtos.AuthResponse;
import mk.roommatematch.auth.AuthDtos.CurrentUserResponse;
import mk.roommatematch.auth.AuthDtos.LoginRequest;
import mk.roommatematch.auth.AuthDtos.RegisterRequest;
import mk.roommatematch.auth.AuthDtos.RegisterResponse;
import mk.roommatematch.auth.AuthDtos.ResetPasswordRequest;
import mk.roommatematch.common.FieldValidationException;
import mk.roommatematch.config.JwtProperties;
import mk.roommatematch.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    /** BCrypt rejects longer input, and a Cyrillic character takes two bytes. */
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_LINK = "Врската не важи или е истечена. Побарај нова.";

    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final AccountTokenRepository tokens;
    private final AccountEmails emails;
    private final AccountProperties accountProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository users, ProfileRepository profiles, AccountTokenRepository tokens,
                       AccountEmails emails, AccountProperties accountProperties, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.users = users;
        this.profiles = profiles;
        this.tokens = tokens;
        this.emails = emails;
        this.accountProperties = accountProperties;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    /** The account stays locked until the student opens the link sent to the address. */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        User user = create(request);
        sendConfirmation(user, Instant.now());
        return new RegisterResponse(user.getEmail());
    }

    /** Demo accounts use made-up addresses, so there is no inbox to confirm them from. */
    @Transactional
    public void registerConfirmed(RegisterRequest request) {
        create(request).verifyEmail();
    }

    /** The new link is kept even though login fails, so it must not be rolled back. */
    @Transactional(noRollbackFor = EmailNotVerifiedException.class)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email()))
                .filter(found -> fitsBcrypt(request.password()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Погрешна е-пошта или лозинка."));
        // Checked only after the password, so nobody learns from this whether an address is registered.
        if (!user.isEmailVerified()) {
            // The first link may have expired or never arrived, so the right password brings a fresh one.
            Instant now = Instant.now();
            if (!sentRecently(user, Purpose.VERIFY_EMAIL, now)) {
                sendConfirmation(user, now);
            }
            throw new EmailNotVerifiedException();
        }
        return new AuthResponse(issueToken(user, Instant.now()));
    }

    /** Says nothing about whether the address exists, so it cannot be used to find accounts. */
    @Transactional
    public void resendConfirmation(String email) {
        Instant now = Instant.now();
        users.findByEmail(normalize(email))
                .filter(user -> !user.isEmailVerified())
                .filter(user -> !sentRecently(user, Purpose.VERIFY_EMAIL, now))
                .ifPresent(user -> sendConfirmation(user, now));
    }

    @Transactional
    public AuthResponse verifyEmail(String token) {
        Instant now = Instant.now();
        User user = useToken(token, Purpose.VERIFY_EMAIL, now);
        user.verifyEmail();
        return new AuthResponse(issueToken(user, now));
    }

    /** Says nothing about whether the address exists, so it cannot be used to find accounts. */
    @Transactional
    public void requestPasswordReset(String email) {
        Instant now = Instant.now();
        users.findByEmail(normalize(email))
                .filter(user -> !sentRecently(user, Purpose.RESET_PASSWORD, now))
                .ifPresent(user -> emails.sendPasswordReset(user.getEmail(),
                        newToken(user, Purpose.RESET_PASSWORD, now, accountProperties.resetValidFor()),
                        accountProperties.resetValidFor()));
    }

    /**
     * Opening the link proves the address belongs to the student, so it also counts as confirming it.
     * Sessions on other devices end, because their tokens were issued before the change.
     */
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        if (!fitsBcrypt(request.password())) {
            throw new FieldValidationException(Map.of("password", "Лозинката е предолга."));
        }
        Instant now = Instant.now();
        User user = useToken(request.token(), Purpose.RESET_PASSWORD, now);
        user.changePassword(passwordEncoder.encode(request.password()));
        user.verifyEmail();
        return new AuthResponse(issueToken(user, now));
    }

    /** Logging out ends every session of the account, so a token copied anywhere stops working too. */
    @Transactional
    public void logout(Long userId) {
        users.findById(userId).ifPresent(User::endSessions);
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new CurrentUserResponse(user.getId(), user.getEmail(), profiles.existsById(userId));
    }

    private User create(RegisterRequest request) {
        if (!fitsBcrypt(request.password())) {
            throw new FieldValidationException(Map.of("password", "Лозинката е предолга."));
        }
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Веќе постои акаунт со оваа е-пошта.");
        }
        return users.save(new User(email, passwordEncoder.encode(request.password())));
    }

    private void sendConfirmation(User user, Instant now) {
        String token = newToken(user, Purpose.VERIFY_EMAIL, now, accountProperties.verificationValidFor());
        emails.sendConfirmation(user.getEmail(), token, accountProperties.verificationValidFor());
    }

    private boolean sentRecently(User user, Purpose purpose, Instant now) {
        return tokens.existsByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(), purpose, now.minus(accountProperties.resendAfter()));
    }

    /** Only the hash is stored; the token itself exists only in the e-mail. */
    private String newToken(User user, Purpose purpose, Instant now, java.time.Duration validFor) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new AccountToken(user.getId(), purpose, sha256(token), now, now.plus(validFor)));
        return token;
    }

    /** Every link of that kind stops working, so an older e-mail cannot be used afterwards. */
    private User useToken(String token, Purpose purpose, Instant now) {
        AccountToken found = tokens.findByTokenHashAndPurpose(sha256(token), purpose)
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK));
        tokens.useAll(found.getUserId(), purpose, now);
        return users.findById(found.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_LINK));
    }

    private String issueToken(User user, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim(CurrentPasswordValidator.SESSION_VERSION, user.getSessionVersion())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.expiration()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
