package mk.roommatematch.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    /**
     * Creates the pair's conversation unless it exists. Two clicks at once both end up with the same
     * conversation instead of one of them failing on the unique pair.
     */
    @Modifying
    @Query(value = """
            insert into conversations (user_low_id, user_high_id) values (:low, :high)
            on conflict (user_low_id, user_high_id) do nothing
            """, nativeQuery = true)
    void createIfMissing(@Param("low") Long low, @Param("high") Long high);

    /** A conversation nobody has written in yet stays out of the list. */
    @Query("""
            select c from Conversation c
            where (c.userLowId = :userId or c.userHighId = :userId) and c.lastMessageAt is not null
            order by c.lastMessageAt desc
            """)
    List<Conversation> findActiveFor(@Param("userId") Long userId);
}
