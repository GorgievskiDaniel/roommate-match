package mk.roommatematch.auth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mk.roommatematch.common.FieldValidationException;
import mk.roommatematch.listing.ImageStorage;
import mk.roommatematch.listing.Listing;
import mk.roommatematch.listing.ListingImage;
import mk.roommatematch.listing.ListingRepository;
import mk.roommatematch.profile.Profile;
import mk.roommatematch.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deletes an account for good. The database removes everything tied to it (profile, listings it owns,
 * conversations, messages, blocks, explanations, assistant history); the photos are removed from disk
 * once that is committed.
 */
@Service
public class AccountDeletionService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ListingRepository listings;
    private final ImageStorage images;
    private final PasswordEncoder passwordEncoder;

    public AccountDeletionService(UserRepository users, ProfileRepository profiles, ListingRepository listings,
                                  ImageStorage images, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.profiles = profiles;
        this.listings = listings;
        this.images = images;
        this.passwordEncoder = passwordEncoder;
    }

    /** The password is asked again, so a computer left logged in is not enough to delete the account. */
    @Transactional
    public void delete(Long userId, String password) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        boolean matches = password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES
                && passwordEncoder.matches(password, user.getPasswordHash());
        if (!matches) {
            throw new FieldValidationException(Map.of("password", "Погрешна лозинка."));
        }
        List<String> files = new ArrayList<>();
        profiles.findById(userId).map(Profile::getAvatar).ifPresent(files::add);
        for (Listing listing : listings.findByOwnerId(userId)) {
            listing.getImages().stream().map(ListingImage::getFileName).forEach(files::add);
        }
        users.delete(user);
        users.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                files.forEach(images::delete);
            }
        });
    }
}
