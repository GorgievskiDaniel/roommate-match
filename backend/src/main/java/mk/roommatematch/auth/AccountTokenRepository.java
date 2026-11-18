package mk.roommatematch.auth;

import java.time.Instant;
import java.util.Optional;
import mk.roommatematch.auth.AccountToken.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, Purpose purpose);

    boolean existsByUserIdAndPurposeAndCreatedAtAfter(Long userId, Purpose purpose, Instant after);

    /** Once one link has worked, the older ones for the same purpose must not. */
    @Modifying
    @Query("update AccountToken t set t.usedAt = :now where t.userId = :userId and t.purpose = :purpose and t.usedAt is null")
    void useAll(@Param("userId") Long userId, @Param("purpose") Purpose purpose, @Param("now") Instant now);
}
