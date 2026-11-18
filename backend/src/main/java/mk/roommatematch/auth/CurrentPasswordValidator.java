package mk.roommatematch.auth;

import java.util.Optional;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects tokens of deleted accounts and tokens issued before the last password change, so a new
 * password logs out every other device.
 */
public class CurrentPasswordValidator implements OAuth2TokenValidator<Jwt> {

    /** The claim that holds the account's session version when the token was issued. */
    public static final String SESSION_VERSION = "sv";

    private static final OAuth2TokenValidatorResult OUTDATED = OAuth2TokenValidatorResult.failure(
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The token is no longer valid", null));

    private final UserRepository users;

    public CurrentPasswordValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Optional<User> user;
        try {
            user = users.findById(Long.valueOf(jwt.getSubject()));
        } catch (NumberFormatException e) {
            return OUTDATED;
        }
        // Tokens from before the claim existed belong to version 0.
        Number version = jwt.getClaim(SESSION_VERSION);
        int issuedFor = version == null ? 0 : version.intValue();
        return user.isPresent() && user.get().getSessionVersion() == issuedFor
                ? OAuth2TokenValidatorResult.success()
                : OUTDATED;
    }
}
