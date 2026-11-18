package mk.roommatematch.support;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mk.roommatematch.mail.Mailer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Keeps e-mails in memory instead of sending them, so tests can open the links. */
@Primary
@Component
public class RecordingMailer implements Mailer {

    public record Sent(String to, String subject, String text) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String text) {
        sent.add(new Sent(to, subject, text));
    }

    public List<Sent> sentTo(String email) {
        String address = email.toLowerCase(Locale.ROOT);
        return sent.stream().filter(mail -> mail.to().equals(address)).toList();
    }

    /** The token from the newest link to the given page, such as "/verify-email". */
    public String lastToken(String email, String path) {
        List<Sent> mails = sentTo(email);
        Pattern link = Pattern.compile(Pattern.quote(path) + "\\?token=([A-Za-z0-9_-]+)");
        for (int i = mails.size() - 1; i >= 0; i--) {
            Matcher matcher = link.matcher(mails.get(i).text());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("No link to " + path + " was sent to " + email);
    }
}
