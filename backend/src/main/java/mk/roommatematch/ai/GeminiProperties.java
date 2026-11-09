package mk.roommatematch.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.gemini")
public record GeminiProperties(String apiKey, String model, String baseUrl, String apiRevision, Duration timeout) {
}
