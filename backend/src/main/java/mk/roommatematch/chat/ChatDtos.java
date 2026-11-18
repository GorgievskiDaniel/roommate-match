package mk.roommatematch.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record StartRequest(@NotNull(message = "Изберете корисник.") Long userId) {
    }

    public record SendRequest(
            @NotBlank(message = "Напишете порака.")
            @Size(max = 2000, message = "Пораката може да има најмногу 2000 знаци.")
            String body) {
    }

    public record ConversationResponse(
            Long id,
            Long otherUserId,
            String otherName,
            String lastMessage,
            Instant lastMessageAt,
            long unread) {
    }

    public record MessageResponse(Long id, Long conversationId, Long senderId, String body, Instant createdAt) {
    }

    public record UnreadResponse(long unread) {
    }

    public record BlockedUser(Long userId, String firstName) {
    }

    /** A stored message and who else should receive it right away. */
    record SentMessage(MessageResponse message, Long recipientId) {
    }
}
