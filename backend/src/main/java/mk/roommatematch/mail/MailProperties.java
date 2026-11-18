package mk.roommatematch.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param from        the sender shown to students
 * @param frontendUrl where the links in the e-mails lead, without a trailing slash
 */
@Validated
@ConfigurationProperties("app.mail")
public record MailProperties(@NotBlank String from, @NotBlank String frontendUrl) {
}
