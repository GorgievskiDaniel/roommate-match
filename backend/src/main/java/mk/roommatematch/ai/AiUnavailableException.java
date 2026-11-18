package mk.roommatematch.ai;

/** The language model could not give a usable answer. The rest of the application keeps working. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
        this.temporary = false;
    }

    private final boolean temporary;

    public AiUnavailableException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public AiUnavailableException(String message, Throwable cause, boolean temporary) {
        super(message, cause);
        this.temporary = temporary;
    }

    /** The model was busy or failed on its side, so another model may still answer. */
    public boolean isTemporary() {
        return temporary;
    }
}
