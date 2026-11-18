package mk.roommatematch.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param webSearch whether a question the documents cannot answer is looked up on Google
 */
@ConfigurationProperties("app.rag")
public record RagProperties(String documentsDir, boolean ingestOnStartup, int results, boolean webSearch) {
}
