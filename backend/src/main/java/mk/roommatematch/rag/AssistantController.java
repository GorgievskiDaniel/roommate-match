package mk.roommatematch.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import mk.roommatematch.rag.AssistantService.AssistantResponse;
import mk.roommatematch.rag.AssistantService.HistoryEntry;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssistantController {

    record QuestionRequest(
            @NotBlank(message = "Напишете прашање.")
            @Size(max = 500, message = "Прашањето може да има најмногу 500 знаци.")
            String question) {
    }

    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @PostMapping("/api/assistant")
    AssistantResponse ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody QuestionRequest request) {
        return service.ask(userId(jwt), request.question().trim());
    }

    @GetMapping("/api/assistant/history")
    List<HistoryEntry> history(@AuthenticationPrincipal Jwt jwt) {
        return service.history(userId(jwt));
    }

    @DeleteMapping("/api/assistant/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear(@AuthenticationPrincipal Jwt jwt) {
        service.clear(userId(jwt));
    }

    @GetMapping("/api/assistant/files")
    ResponseEntity<Resource> file(@RequestParam String name) {
        return ResponseEntity.ok()
                .contentType(name.toLowerCase().endsWith(".pdf")
                        ? MediaType.APPLICATION_PDF
                        : new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(service.file(name));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
