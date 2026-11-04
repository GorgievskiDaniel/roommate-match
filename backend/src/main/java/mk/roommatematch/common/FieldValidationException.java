package mk.roommatematch.common;

import java.util.Map;

/** A request that passed annotation checks but breaks a rule spanning several fields. */
public class FieldValidationException extends RuntimeException {

    private final transient Map<String, String> errors;

    public FieldValidationException(Map<String, String> errors) {
        super("Validation failed: " + errors.keySet());
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
