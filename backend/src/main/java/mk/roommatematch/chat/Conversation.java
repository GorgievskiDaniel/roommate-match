package mk.roommatematch.chat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userLowId;

    private Long userHighId;

    private Instant lastMessageAt;

    protected Conversation() {
    }

    public Conversation(Long firstUserId, Long secondUserId) {
        userLowId = Math.min(firstUserId, secondUserId);
        userHighId = Math.max(firstUserId, secondUserId);
    }

    public boolean hasParticipant(Long userId) {
        return userLowId.equals(userId) || userHighId.equals(userId);
    }

    public Long otherParticipant(Long userId) {
        return userLowId.equals(userId) ? userHighId : userLowId;
    }

    public void messageSentAt(Instant time) {
        lastMessageAt = time;
    }

    public Long getId() {
        return id;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }
}
