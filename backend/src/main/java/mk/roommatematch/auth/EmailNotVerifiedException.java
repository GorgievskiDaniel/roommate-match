package mk.roommatematch.auth;

/** The password was right, but the address has not been confirmed yet. */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("The e-mail address is not confirmed");
    }
}
