package com.study.grabthisforme.auth;

import com.study.grabthisforme.common.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionChannelInterceptor implements ChannelInterceptor {
    private static final Set<StompCommand> PROTECTED_COMMANDS = Set.of(
        StompCommand.SUBSCRIBE,
        StompCommand.SEND,
        StompCommand.ACK,
        StompCommand.NACK
    );

    private final TokenService tokenService;

    public AuthSessionChannelInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!PROTECTED_COMMANDS.contains(accessor.getCommand())) return message;
        if (!(accessor.getUser() instanceof StompPrincipal principal)
            || !tokenService.isSessionActive(principal.getName(), principal.userId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 40109, "WebSocket session revoked");
        }
        return message;
    }
}
