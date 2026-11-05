package mk.roommatematch.listing;

import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public because an img tag cannot send a token. The random file names cannot be guessed. */
@RestController
public class ImageController {

    private final ImageStorage storage;

    public ImageController(ImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/images/{fileName}")
    ResponseEntity<Resource> image(@PathVariable String fileName) {
        return storage.load(fileName)
                .map(resource -> ResponseEntity.ok()
                        .contentType(mediaType(fileName))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)))
                        .body(resource))
                .orElse(ResponseEntity.notFound().build());
    }

    private static MediaType mediaType(String fileName) {
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        return fileName.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
    }
}
