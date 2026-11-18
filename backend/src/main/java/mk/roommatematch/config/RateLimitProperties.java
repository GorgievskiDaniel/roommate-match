package mk.roommatematch.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How many requests fit in one minute.
 *
 * @param auth      login, registration and the e-mail links, per address and per action
 * @param ai        requests that call the language model, per student
 * @param addresses address suggestions while typing, per student
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@Positive int auth, @Positive int ai, @Positive int addresses) {
}
