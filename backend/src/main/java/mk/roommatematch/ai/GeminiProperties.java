package mk.roommatematch.ai;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.gemini")
public record GeminiProperties(String apiKey, String model, List<String> fallbackModels, String embeddingModel,
                               String baseUrl, String apiRevision, Duration timeout, String thinkingLevel) {

    public GeminiProperties {
        fallbackModels = fallbackModels == null ? List.of() : List.copyOf(fallbackModels);
    }

    /** The main model first, then the ones tried when it is busy. */
    public List<String> models() {
        return Stream.concat(Stream.of(model), fallbackModels.stream()).toList();
    }
}
