package mk.roommatematch.config;

import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Delivers new messages to the browser. Sending goes through REST, so the socket only ever
 * carries data from server to client, and each user can listen only to their own queue.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String BEARER = "Bearer ";
    private static final String OWN_QUEUE_PREFIX = "/user/queue/";

    private final JwtDecoder jwtDecoder;
    private final String[] allowedOrigins;

    public WebSocketConfig(JwtDecoder jwtDecoder,
                           @Value("${app.websocket.allowed-origins}") String[] allowedOrigins) {
        this.jwtDecoder = jwtDecoder;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && accessor.getCommand() != null) {
                    check(accessor);
                }
                return message;
            }
        });
    }

    private void check(StompHeaderAccessor accessor) {
        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> {
                String destination = accessor.getDestination();
                if (accessor.getUser() == null || destination == null || !destination.startsWith(OWN_QUEUE_PREFIX)) {
                    throw new MessagingException("Subscription not allowed");
                }
            }
            case SEND -> throw new MessagingException("Sending goes through the REST API");
            default -> {
            }
        }
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessagingException("Missing token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(BEARER.length()));
            String userId = jwt.getSubject();
            return () -> userId;
        } catch (JwtException e) {
            throw new MessagingException("Invalid token");
        }
    }
}
