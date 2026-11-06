package mk.roommatematch.chat;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import mk.roommatematch.chat.ChatDtos.BlockedUser;
import mk.roommatematch.chat.ChatDtos.ConversationResponse;
import mk.roommatematch.chat.ChatDtos.MessageResponse;
import mk.roommatematch.chat.ChatDtos.SentMessage;
import mk.roommatematch.profile.Profile;
import mk.roommatematch.profile.ProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    static final int PAGE_SIZE = 50;
    private static final String UNKNOWN_NAME = "Корисник";

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final BlockRepository blocks;
    private final ProfileRepository profiles;

    public ChatService(ConversationRepository conversations, MessageRepository messages, BlockRepository blocks,
                       ProfileRepository profiles) {
        this.conversations = conversations;
        this.messages = messages;
        this.blocks = blocks;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(Long userId) {
        Set<Long> hidden = blockedEitherWay(userId);
        List<Conversation> visible = conversations.findActiveFor(userId).stream()
                .filter(c -> !hidden.contains(c.otherParticipant(userId)))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> unread = messages.countUnread(visible.stream().map(Conversation::getId).toList(), userId)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, String> names = firstNames(visible.stream().map(c -> c.otherParticipant(userId)).toList());
        return visible.stream()
                .map(c -> toResponse(c, userId, names, unread.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return list(userId).stream().mapToLong(ConversationResponse::unread).sum();
    }

    @Transactional
    public ConversationResponse start(Long userId, Long otherId) {
        if (userId.equals(otherId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не можете да си пишете сами на себе.");
        }
        if (!profiles.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Прво пополнете го профилот.");
        }
        if (!profiles.existsById(otherId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Корисникот не постои.");
        }
        requireNotBlocked(userId, otherId);
        Conversation conversation = conversations
                .findByUserLowIdAndUserHighId(Math.min(userId, otherId), Math.max(userId, otherId))
                .orElseGet(() -> conversations.save(new Conversation(userId, otherId)));
        return describe(conversation, userId);
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(Long id, Long userId) {
        return describe(findVisible(id, userId), userId);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> messages(Long id, Long userId, Long beforeId) {
        findVisible(id, userId);
        PageRequest page = PageRequest.of(0, PAGE_SIZE);
        List<ChatMessage> newestFirst = beforeId == null
                ? messages.findByConversationIdOrderByIdDesc(id, page)
                : messages.findByConversationIdAndIdLessThanOrderByIdDesc(id, beforeId, page);
        return newestFirst.reversed().stream().map(ChatService::toResponse).toList();
    }

    @Transactional
    SentMessage send(Long id, Long userId, String body) {
        Conversation conversation = findParticipating(id, userId);
        Long recipient = conversation.otherParticipant(userId);
        requireNotBlocked(userId, recipient);
        Instant now = Instant.now();
        ChatMessage saved = messages.save(new ChatMessage(id, userId, body.trim(), now));
        conversation.messageSentAt(now);
        return new SentMessage(toResponse(saved), recipient);
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        findVisible(id, userId);
        messages.markRead(id, userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<BlockedUser> blockedBy(Long userId) {
        List<Long> ids = blocks.findByBlockerId(userId).stream().map(Block::getBlockedId).toList();
        Map<Long, String> names = firstNames(ids);
        return ids.stream().map(id -> new BlockedUser(id, names.getOrDefault(id, UNKNOWN_NAME))).toList();
    }

    @Transactional
    public void block(Long userId, Long otherId) {
        if (userId.equals(otherId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не можете да се блокирате себеси.");
        }
        if (!profiles.existsById(otherId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Корисникот не постои.");
        }
        if (blocks.findByBlockerIdAndBlockedId(userId, otherId).isEmpty()) {
            blocks.save(new Block(userId, otherId));
        }
    }

    @Transactional
    public void unblock(Long userId, Long otherId) {
        blocks.findByBlockerIdAndBlockedId(userId, otherId).ifPresent(blocks::delete);
    }

    private ConversationResponse describe(Conversation conversation, Long userId) {
        Long other = conversation.otherParticipant(userId);
        return toResponse(conversation, userId, firstNames(List.of(other)), 0);
    }

    private ConversationResponse toResponse(Conversation c, Long userId, Map<Long, String> names, long unread) {
        Long other = c.otherParticipant(userId);
        String last = messages.findFirstByConversationIdOrderByIdDesc(c.getId()).map(ChatMessage::getBody).orElse(null);
        return new ConversationResponse(c.getId(), other, names.getOrDefault(other, UNKNOWN_NAME), last,
                c.getLastMessageAt(), unread);
    }

    private static MessageResponse toResponse(ChatMessage m) {
        return new MessageResponse(m.getId(), m.getConversationId(), m.getSenderId(), m.getBody(), m.getCreatedAt());
    }

    private Map<Long, String> firstNames(List<Long> ids) {
        return profiles.findAllById(ids).stream().collect(Collectors.toMap(Profile::getUserId, Profile::getFirstName));
    }

    /** Someone else's conversation looks the same as one that does not exist. */
    private Conversation findParticipating(Long id, Long userId) {
        return conversations.findById(id)
                .filter(c -> c.hasParticipant(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Разговорот не постои."));
    }

    /** While a block lasts, the conversation is hidden from both sides. */
    private Conversation findVisible(Long id, Long userId) {
        Conversation conversation = findParticipating(id, userId);
        if (blockedEitherWay(userId).contains(conversation.otherParticipant(userId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Разговорот не постои.");
        }
        return conversation;
    }

    private void requireNotBlocked(Long userId, Long otherId) {
        if (blockedEitherWay(userId).contains(otherId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Не можете да разменувате пораки со овој корисник.");
        }
    }

    private Set<Long> blockedEitherWay(Long userId) {
        Set<Long> result = new HashSet<>();
        blocks.findByBlockerId(userId).forEach(block -> result.add(block.getBlockedId()));
        blocks.findByBlockedId(userId).forEach(block -> result.add(block.getBlockerId()));
        return result;
    }
}
