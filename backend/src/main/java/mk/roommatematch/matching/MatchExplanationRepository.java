package mk.roommatematch.matching;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MatchExplanationRepository extends JpaRepository<MatchExplanation, Long> {

    Optional<MatchExplanation> findByViewerIdAndOtherId(Long viewerId, Long otherId);

    @Modifying
    @Transactional
    @Query(value = """
            insert into match_explanations (viewer_id, other_id, input_hash, text)
            values (:viewerId, :otherId, :hash, :text)
            on conflict (viewer_id, other_id)
            do update set input_hash = excluded.input_hash, text = excluded.text, created_at = now()
            """, nativeQuery = true)
    void store(@Param("viewerId") Long viewerId, @Param("otherId") Long otherId,
               @Param("hash") String hash, @Param("text") String text);
}
