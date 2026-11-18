package mk.roommatematch.auth;

import java.time.Duration;
import mk.roommatematch.mail.MailProperties;
import mk.roommatematch.mail.Mailer;
import org.springframework.stereotype.Component;

/** The two e-mails an account can receive. */
@Component
public class AccountEmails {

    private final Mailer mailer;
    private final MailProperties properties;

    public AccountEmails(Mailer mailer, MailProperties properties) {
        this.mailer = mailer;
        this.properties = properties;
    }

    public void sendConfirmation(String to, String token, Duration validFor) {
        mailer.send(to, "Потврди ја е-поштата за Roommate Match", """
                Здраво,

                За да го активираш акаунтот на Roommate Match, отвори го следниов линк:
                %s

                Линкот важи %s. Доколку не сте се регистрирале вие, игнорирајте ја пораката.
                """.formatted(link("/verify-email", token), hours(validFor)));
    }

    public void sendPasswordReset(String to, String token, Duration validFor) {
        mailer.send(to, "Нова лозинка за Roommate Match", """
                Здраво,

                Побарана е нова лозинка за твојот акаунт на Roommate Match. Постави ја на следниот линк:
                %s

                Линкот важи %s и може да се искористи само еднаш. Ако не побара ти, занемари ја оваа
                порака и лозинката останува иста.
                """.formatted(link("/reset-password", token), hours(validFor)));
    }

    private String link(String path, String token) {
        return properties.frontendUrl() + path + "?token=" + token;
    }

    private static String hours(Duration duration) {
        long hours = Math.max(1, duration.toHours());
        return hours == 1 ? "1 час" : hours + " часа";
    }
}
