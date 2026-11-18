package mk.roommatematch.listing;

import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

    /** Stores a checked image under a new random name and returns that name. */
    String store(MultipartFile file);

    Optional<Resource> load(String fileName);

    void delete(String fileName);
}
