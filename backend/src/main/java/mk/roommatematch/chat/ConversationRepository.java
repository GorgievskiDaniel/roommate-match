package mk.roommatematch.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    /** A conversation nobody has written in yet stays out of the list. */
    @Query("""
            select c from Conversation c
            where (c.userLowId = :userId or c.userHighId = :userId) and c.lastMessageAt is not null
            order by c.lastMessageAt desc
            """)
    List<Conversation> findActiveFor(@Param("userId") Long userId);
}
