package mk.roommatematch.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param cookieSecure whether the login cookie is sent over HTTPS only; true in production
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(@Size(min = 32) String secret, @NotNull Duration expiration, boolean cookieSecure) {
}
