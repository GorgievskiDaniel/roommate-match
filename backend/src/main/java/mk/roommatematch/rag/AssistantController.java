package mk.roommatematch.rag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mk.roommatematch.rag.AssistantService.AssistantResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    AssistantResponse ask(@Valid @RequestBody QuestionRequest request) {
        return service.ask(request.question().trim());
    }
}
