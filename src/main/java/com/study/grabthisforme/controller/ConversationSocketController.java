package com.study.grabthisforme.controller;

import com.study.grabthisforme.auth.StompPrincipal;
import com.study.grabthisforme.service.ConversationService;
import com.study.grabthisforme.service.PushService;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ConversationSocketController {

    private final ConversationService conversationService;
    private final PushService pushService;

    public ConversationSocketController(
        ConversationService conversationService,
        PushService pushService
    ) {
        this.conversationService = conversationService;
        this.pushService = pushService;
    }

    @MessageMapping("/conversations/read")
    public void markConversationRead(ReadConversationSocketRequest request, Principal principal) {
        if (request == null || request.conversationId() == null || request.conversationId().isBlank() || principal == null) {
            return;
        }
        conversationService.markRead(
            userId(principal),
            request.conversationId(),
            request.lastReadTime()
        );
    }

    @MessageMapping("/push/ack")
    public void acknowledgePushDelivery(PushAckSocketRequest request, Principal principal) {
        if (request == null || request.deliveryAckId() == null || request.deliveryAckId().isBlank() || principal == null) {
            return;
        }
        pushService.acknowledgeDelivery(
            userId(principal),
            request.deliveryAckId()
        );
    }

    private long userId(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) {
            return stompPrincipal.userId();
        }
        throw new IllegalStateException("Missing authenticated websocket identity");
    }

    public record ReadConversationSocketRequest(
        String conversationId,
        Long lastReadTime
    ) {
    }

    public record PushAckSocketRequest(
        String deliveryAckId
    ) {
    }
}
