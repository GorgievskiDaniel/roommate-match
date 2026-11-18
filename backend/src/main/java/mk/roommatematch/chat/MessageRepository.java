package mk.roommatematch.chat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable page);

    List<ChatMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId, Pageable page);

    Optional<ChatMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    /** Rows of [conversation id, messages the user has not read there]. */
    @Query("""
            select m.conversationId, count(m) from ChatMessage m
            where m.conversationId in :ids and m.senderId <> :userId and m.readAt is null
            group by m.conversationId
            """)
    List<Object[]> countUnread(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    @Modifying
    @Query("""
            update ChatMessage m set m.readAt = :now
            where m.conversationId = :conversationId and m.senderId <> :userId and m.readAt is null
            """)
    void markRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("now") Instant now);
}
