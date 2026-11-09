package mk.roommatematch.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rag")
public record RagProperties(String documentsDir, boolean ingestOnStartup, int results) {
}
