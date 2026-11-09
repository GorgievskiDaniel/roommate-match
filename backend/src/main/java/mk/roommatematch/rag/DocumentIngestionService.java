package mk.roommatematch.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import mk.roommatematch.ai.AiUnavailableException;
import mk.roommatematch.ai.GeminiClient;
import mk.roommatematch.reference.Area;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormRepository;
import mk.roommatematch.reference.DormRoomOption;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

/**
 * Keeps the search index in step with the documents folder. Only new or changed documents are
 * sent for embedding, so restarting the application costs nothing.
 */
@Service
public class DocumentIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final String DORM_FACTS_SOURCE = "__dorm_facts__";
    private static final String DORM_FACTS_TITLE = "Основни податоци за домовите";
    private static final Set<String> SUPPORTED = Set.of("txt", "md", "pdf");
    private static final int BATCH_SIZE = 100;

    private final RagProperties properties;
    private final RagStore store;
    private final GeminiClient gemini;
    private final DormRepository dorms;

    public DocumentIngestionService(RagProperties properties, RagStore store, GeminiClient gemini, DormRepository dorms) {
        this.properties = properties;
        this.store = store;
        this.gemini = gemini;
        this.dorms = dorms;
    }

    /** A problem here must not stop the application; the assistant then just knows less. */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.ingestOnStartup()) {
            return;
        }
        try {
            ingest();
        } catch (AiUnavailableException | IOException e) {
            log.warn("Document ingestion did not finish: {}", e.getMessage());
        }
    }

    public void ingest() throws IOException {
        Map<String, String> known = store.documentHashes();
        Set<String> seen = new HashSet<>();

        seen.add(DORM_FACTS_SOURCE);
        update(DORM_FACTS_SOURCE, DORM_FACTS_TITLE, dormFacts(), known);

        Path folder = Path.of(properties.documentsDir());
        if (Files.isDirectory(folder)) {
            List<Path> files;
            try (Stream<Path> stream = Files.list(folder)) {
                files = stream.filter(Files::isRegularFile).filter(DocumentIngestionService::isSupported).sorted().toList();
            }
            for (Path file : files) {
                String name = file.getFileName().toString();
                String text = read(file);
                if (text.isBlank()) {
                    log.warn("Skipping {}: no text could be read from it", name);
                    continue;
                }
                seen.add(name);
                update(name, titleOf(name), text, known);
            }
        }
        for (String source : known.keySet()) {
            if (!seen.contains(source)) {
                store.delete(source);
                log.info("Removed document {}", source);
            }
        }
    }

    private void update(String source, String title, String text, Map<String, String> known) {
        String hash = sha256(text);
        if (hash.equals(known.get(source))) {
            return;
        }
        List<String> chunks = DocumentChunker.chunk(text);
        List<double[]> embeddings = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += BATCH_SIZE) {
            List<String> batch = chunks.subList(start, Math.min(start + BATCH_SIZE, chunks.size())).stream()
                    .map(chunk -> "title: " + title + " | text: " + chunk)
                    .toList();
            embeddings.addAll(gemini.embed(batch));
        }
        store.replace(source, title, hash, chunks, embeddings);
        log.info("Indexed {} ({} pieces)", source, chunks.size());
    }

    /** What the application itself knows for certain about the dorms. */
    private String dormFacts() {
        List<String> paragraphs = new ArrayList<>();
        for (Dorm dorm : dorms.findAllWithRoomOptions()) {
            StringBuilder text = new StringBuilder(dorm.getName());
            if (dorm.getLatitude() != null && dorm.getLongitude() != null) {
                Area area = Area.nearestTo(dorm.getLatitude(), dorm.getLongitude());
                text.append(" е студентски дом во ").append(area.isSkopje() ? "Скопје" : area.getLabel()).append('.');
            } else {
                text.append(" е студентски дом.");
            }
            if (!dorm.getRoomOptions().isEmpty()) {
                text.append(" Типови соби: ")
                        .append(String.join(", ", dorm.getRoomOptions().stream().map(DormRoomOption::getName).toList()))
                        .append('.');
            }
            paragraphs.add(text.toString());
        }
        return String.join("\n\n", paragraphs);
    }

    private static boolean isSupported(Path file) {
        return SUPPORTED.contains(extension(file.getFileName().toString()));
    }

    private static String read(Path file) throws IOException {
        if ("pdf".equals(extension(file.getFileName().toString()))) {
            try (PDDocument document = Loader.loadPDF(file.toFile())) {
                return new PDFTextStripper().getText(document);
            }
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String titleOf(String name) {
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)).replace('_', ' ').replace('-', ' ').trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
