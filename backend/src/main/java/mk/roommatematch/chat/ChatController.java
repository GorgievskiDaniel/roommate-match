package mk.roommatematch.chat;

import jakarta.validation.Valid;
import java.util.List;
import mk.roommatematch.chat.ChatDtos.BlockedUser;
import mk.roommatematch.chat.ChatDtos.ConversationResponse;
import mk.roommatematch.chat.ChatDtos.MessageResponse;
import mk.roommatematch.chat.ChatDtos.SendRequest;
import mk.roommatematch.chat.ChatDtos.SentMessage;
import mk.roommatematch.chat.ChatDtos.StartRequest;
import mk.roommatematch.chat.ChatDtos.UnreadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    static final String QUEUE = "/queue/messages";

    private final ChatService service;
    private final SimpMessagingTemplate broker;

    public ChatController(ChatService service, SimpMessagingTemplate broker) {
        this.service = service;
        this.broker = broker;
    }

    @GetMapping("/conversations")
    List<ConversationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    @GetMapping("/conversations/unread")
    UnreadResponse unread(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadResponse(service.unreadCount(userId(jwt)));
    }

    @PostMapping("/conversations")
    ConversationResponse start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest request) {
        return service.start(userId(jwt), request.userId());
    }

    @GetMapping("/conversations/{id}")
    ConversationResponse get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, userId(jwt));
    }

    @GetMapping("/conversations/{id}/messages")
    List<MessageResponse> messages(@PathVariable Long id, @RequestParam(required = false) Long before,
                                   @AuthenticationPrincipal Jwt jwt) {
        return service.messages(id, userId(jwt), before);
    }

    /** Delivered only after the service has committed, so nobody sees a message that was not stored. */
    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageResponse send(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt,
                         @Valid @RequestBody SendRequest request) {
        Long sender = userId(jwt);
        SentMessage sent = service.send(id, sender, request.body());
        broker.convertAndSendToUser(sent.recipientId().toString(), QUEUE, sent.message());
        broker.convertAndSendToUser(sender.toString(), QUEUE, sent.message());
        return sent.message();
    }

    @PostMapping("/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        service.markRead(id, userId(jwt));
    }

    @GetMapping("/blocks")
    List<BlockedUser> blocked(@AuthenticationPrincipal Jwt jwt) {
        return service.blockedBy(userId(jwt));
    }

    @PostMapping("/blocks/{otherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void block(@PathVariable Long otherId, @AuthenticationPrincipal Jwt jwt) {
        service.block(userId(jwt), otherId);
    }

    @DeleteMapping("/blocks/{otherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblock(@PathVariable Long otherId, @AuthenticationPrincipal Jwt jwt) {
        service.unblock(userId(jwt), otherId);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
