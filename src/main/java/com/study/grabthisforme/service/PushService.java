package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class PushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public PushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public SseEmitter subscribe(long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        send(emitter, "connected", Map.of("userId", userId));
        return emitter;
    }

    public void pushConversationMessage(String conversationId, List<Long> participantIds, Object payload) {
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, payload);
        for (Long participantId : participantIds) {
            messagingTemplate.convertAndSendToUser(String.valueOf(participantId), "/queue/messages", payload);
            pushToUser(participantId, "conversation.message", payload);
        }
    }

    public void pushToUser(long userId, String eventName, Object payload) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            send(emitter, eventName, payload);
        }
    }

    private void send(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, 50003, "Push delivery failed");
        }
    }

    private void remove(long userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
