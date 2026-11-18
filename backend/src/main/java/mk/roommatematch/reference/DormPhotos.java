package mk.roommatematch.reference;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Photos of the dorms, shipped with the application in dorm-photos/{slug}/1.jpg, 2.jpg and so on.
 * Public like the listing photos, because an img tag cannot send a token.
 */
@RestController
public class DormPhotos {

    private static final Pattern SLUG = Pattern.compile("[a-z-]{1,40}");
    private static final Pattern FILE = Pattern.compile("\\d{1,3}\\.jpg");

    private final Map<String, List<String>> urls = new ConcurrentHashMap<>();

    /**
     * The addresses of a dorm's photos, in the order of their numbers. Each one carries a fingerprint of the
     * file, so after a photo is replaced or the photos are reordered, browsers fetch the new one instead of
     * keeping the old one from their cache.
     */
    public List<String> urls(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            return List.of();
        }
        return urls.computeIfAbsent(slug, key -> {
            try {
                Resource[] found = new PathMatchingResourcePatternResolver()
                        .getResources("classpath:dorm-photos/" + key + "/*.jpg");
                return Arrays.stream(found)
                        .filter(resource -> resource.getFilename() != null
                                && FILE.matcher(resource.getFilename()).matches())
                        .sorted(Comparator.comparingInt(DormPhotos::number))
                        .map(resource -> "/api/dorm-photos/" + key + "/" + resource.getFilename()
                                + "?v=" + fingerprint(resource))
                        .toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static int number(Resource resource) {
        String name = resource.getFilename();
        return Integer.parseInt(name.substring(0, name.indexOf('.')));
    }

    private static String fingerprint(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            CRC32 crc = new CRC32();
            crc.update(in.readAllBytes());
            return Long.toHexString(crc.getValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/api/dorm-photos/{slug}/{file}")
    ResponseEntity<Resource> photo(@PathVariable String slug, @PathVariable String file) {
        if (!SLUG.matcher(slug).matches() || !FILE.matcher(file).matches()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new ClassPathResource("dorm-photos/" + slug + "/" + file);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)))
                .body(resource);
    }
}
