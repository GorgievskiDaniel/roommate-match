package mk.roommatematch.listing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import mk.roommatematch.common.FieldValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalImageStorage implements ImageStorage {

    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp)");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final Path root;

    public LocalImageStorage(@Value("${app.uploads.dir}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            String extension = detectExtension(in.readNBytes(12));
            String name = UUID.randomUUID() + "." + extension;
            Files.createDirectories(root);
            try (InputStream content = file.getInputStream()) {
                Files.copy(content, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<Resource> load(String fileName) {
        if (!STORED_NAME.matcher(fileName).matches()) {
            return Optional.empty();
        }
        Path path = root.resolve(fileName);
        return Files.isRegularFile(path) ? Optional.of(new PathResource(path)) : Optional.empty();
    }

    @Override
    public void delete(String fileName) {
        if (STORED_NAME.matcher(fileName).matches()) {
            try {
                Files.deleteIfExists(root.resolve(fileName));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Looks at the file's content, because a browser-supplied name or type can say anything. */
    private static String detectExtension(byte[] head) {
        if (startsWith(head, JPEG)) {
            return "jpg";
        }
        if (startsWith(head, PNG)) {
            return "png";
        }
        if (head.length >= 12
                && new String(head, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(head, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return "webp";
        }
        throw new FieldValidationException(Map.of("images", "Дозволени се само слики JPG, PNG и WebP."));
    }

    private static boolean startsWith(byte[] head, byte[] prefix) {
        return head.length >= prefix.length && Arrays.equals(head, 0, prefix.length, prefix, 0, prefix.length);
    }
}
