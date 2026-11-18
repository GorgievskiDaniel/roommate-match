package mk.roommatematch.auth;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.accounts")
public record AccountProperties(@NotNull Duration verificationValidFor, @NotNull Duration resetValidFor,
                                @NotNull Duration resendAfter) {
}
