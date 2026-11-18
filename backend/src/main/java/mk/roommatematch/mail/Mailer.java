package mk.roommatematch.mail;

public interface Mailer {

    void send(String to, String subject, String text);
}
