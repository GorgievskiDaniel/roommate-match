package mk.roommatematch.common;

import java.util.LinkedHashMap;
import java.util.Map;
import mk.roommatematch.ai.AiUnavailableException;
import mk.roommatematch.auth.EmailNotVerifiedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AiUnavailableException.class)
    ProblemDetail handleAiUnavailable(AiUnavailableException ex) {
        logger.warn("Language model unavailable: " + ex.getMessage()
                + (ex.getCause() == null ? "" : " (" + ex.getCause().getMessage() + ")"));
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Паметната функција моментално не е достапна. Обидете се повторно подоцна.");
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Е-поштата уште не е потврдена. Ти испративме врска за потврда: отвори ја, па најави се. "
                        + "Ако не ја гледаш, провери ја папката за спам.");
        // Lets the page offer to send the link again.
        problem.setProperty("code", "EMAIL_NOT_VERIFIED");
        return problem;
    }

    /**
     * Two requests that create the same thing at once, such as a double click while registering or
     * joining a listing: the second one hits a unique key. It is a conflict, not a server error.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        logger.info("Conflicting request: " + ex.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Ова веќе е направено, можеби со претходен клик. Освежете ја страницата и обидете се повторно.");
    }

    @ExceptionHandler(FieldValidationException.class)
    ProblemDetail handleFieldValidation(FieldValidationException ex) {
        return validationProblem(ex.getErrors());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(validationProblem(Map.of("images", "Секоја слика може да има најмногу 5 MB.")));
    }

    private ProblemDetail validationProblem(Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Проверете ги внесените податоци.");
        problem.setProperty("errors", errors);
        return problem;
    }
}
