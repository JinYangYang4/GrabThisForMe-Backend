package com.study.grabthisforme.service;

import com.study.grabthisforme.auth.AuthSessionRevokedEvent;
import com.study.grabthisforme.persistence.entity.AuthSessionRevokedReason;
import com.study.grabthisforme.persistence.entity.AuthSessionStatus;
import com.study.grabthisforme.persistence.repository.AuthSessionRepository;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class PushService {

    private static final long ACK_TIMEOUT_MS = 8_000L;
    private static final int MAX_RETRY_COUNT = 3;

    private final SimpMessagingTemplate messagingTemplate;
    private final AuthSessionRepository authSessions;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<String, PendingDelivery> pendingDeliveries = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor();

    public PushService(SimpMessagingTemplate messagingTemplate, AuthSessionRepository authSessions) {
        this.messagingTemplate = messagingTemplate;
        this.authSessions = authSessions;
    }

    public SseEmitter subscribe(long userId, String sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(error -> remove(sessionId, emitter));
        send(emitter, "connected", Map.of("userId", userId, "sessionId", sessionId));
        return emitter;
    }

    public void pushConversationMessage(String conversationId, List<Long> participantIds, Object payload) {
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, payload);
        for (Long participantId : participantIds) {
            pushToUser(participantId, "conversation.message", payload);
        }
    }

    public void pushToUser(long userId, String eventName, Object payload) {
        Map<String, Object> trackedPayload = buildTrackedPayload(eventName, payload);
        sendToUserQueue(userId, trackedPayload);
        trackPendingDelivery(userId, trackedPayload);
        sendToSse(userId, eventName, trackedPayload);
    }

    @EventListener
    public void onSessionRevoked(AuthSessionRevokedEvent event) {
        pushSessionRevoked(event.sessionId(), event.reason());
        closeSseSession(event.sessionId());
    }

    public void pushSessionRevoked(String sessionId, AuthSessionRevokedReason revokedReason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "AUTH_SESSION_REVOKED");
        payload.put("eventId", "evt_" + UUID.randomUUID());
        payload.put("sessionId", sessionId);
        payload.put("code", errorCode(revokedReason));
        payload.put("reason", clientReason(revokedReason));
        payload.put("occurredAt", System.currentTimeMillis());
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/messages", payload);
    }

    public void acknowledgeDelivery(long userId, String deliveryAckId) {
        if (deliveryAckId == null || deliveryAckId.isBlank()) {
            return;
        }
        PendingDelivery pendingDelivery = pendingDeliveries.remove(buildPendingKey(userId, deliveryAckId));
        if (pendingDelivery != null) {
            pendingDelivery.cancel();
        }
    }

    @PreDestroy
    public void shutdown() {
        retryExecutor.shutdownNow();
    }

    private Map<String, Object> buildTrackedPayload(String eventName, Object payload) {
        Map<String, Object> trackedPayload = new HashMap<>();
        if (payload instanceof Map<?, ?> payloadMap) {
            payloadMap.forEach((key, value) -> trackedPayload.put(String.valueOf(key), value));
        } else {
            trackedPayload.put("data", payload);
        }
        trackedPayload.putIfAbsent("type", eventName);
        trackedPayload.putIfAbsent("deliveryAckId", UUID.randomUUID().toString());
        return trackedPayload;
    }

    private void sendToUserQueue(long userId, Map<String, Object> payload) {
        authSessions.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, AuthSessionStatus.ACTIVE)
            .ifPresent(session -> messagingTemplate.convertAndSendToUser(session.id, "/queue/messages", payload));
    }

    private int errorCode(AuthSessionRevokedReason reason) {
        return switch (reason) {
            case REPLACED_BY_NEW_LOGIN -> 40108;
            case USER_LOGOUT -> 40109;
            case PASSWORD_CHANGED, PASSWORD_RESET -> 40110;
            case REFRESH_TOKEN_REUSED, SECURITY_RISK -> 40112;
            case ADMIN_REVOKED -> 40113;
        };
    }

    private String clientReason(AuthSessionRevokedReason reason) {
        return switch (reason) {
            case REPLACED_BY_NEW_LOGIN -> "SESSION_REPLACED";
            case USER_LOGOUT -> "USER_LOGOUT";
            case PASSWORD_CHANGED -> "PASSWORD_CHANGED";
            case PASSWORD_RESET -> "PASSWORD_RESET";
            case ADMIN_REVOKED -> "ADMIN_REVOKED";
            case REFRESH_TOKEN_REUSED -> "REFRESH_TOKEN_REUSED";
            case SECURITY_RISK -> "SECURITY_RISK";
        };
    }

    private void trackPendingDelivery(long userId, Map<String, Object> payload) {
        Object deliveryAckIdValue = payload.get("deliveryAckId");
        if (!(deliveryAckIdValue instanceof String deliveryAckId) || deliveryAckId.isBlank()) {
            return;
        }

        String pendingKey = buildPendingKey(userId, deliveryAckId);
        PendingDelivery previousDelivery = pendingDeliveries.remove(pendingKey);
        if (previousDelivery != null) {
            previousDelivery.cancel();
        }

        PendingDelivery pendingDelivery = new PendingDelivery(userId, deliveryAckId, payload);
        pendingDeliveries.put(pendingKey, pendingDelivery);
        scheduleRetry(pendingKey, pendingDelivery);
    }

    private void scheduleRetry(String pendingKey, PendingDelivery pendingDelivery) {
        ScheduledFuture<?> scheduledFuture = retryExecutor.schedule(
            () -> retryPendingDelivery(pendingKey),
            ACK_TIMEOUT_MS,
            TimeUnit.MILLISECONDS
        );
        pendingDelivery.replaceFuture(scheduledFuture);
    }

    private void retryPendingDelivery(String pendingKey) {
        PendingDelivery pendingDelivery = pendingDeliveries.get(pendingKey);
        if (pendingDelivery == null) {
            return;
        }

        int nextRetryCount = pendingDelivery.incrementRetryCount();
        if (nextRetryCount > MAX_RETRY_COUNT) {
            pendingDeliveries.remove(pendingKey, pendingDelivery);
            pendingDelivery.cancel();
            return;
        }
        if (pendingDeliveries.get(pendingKey) != pendingDelivery) {
            return;
        }

        sendToUserQueue(pendingDelivery.userId(), pendingDelivery.payload());
        scheduleRetry(pendingKey, pendingDelivery);
    }

    private void sendToSse(long userId, String eventName, Object payload) {
        String sessionId = authSessions
            .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, AuthSessionStatus.ACTIVE)
            .map(session -> session.id)
            .orElse(null);
        if (sessionId == null) return;
        List<SseEmitter> userEmitters = emitters.get(sessionId);
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
            emitter.completeWithError(exception);
        }
    }

    private void remove(String sessionId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(sessionId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(sessionId);
            }
        }
    }

    private void closeSseSession(String sessionId) {
        List<SseEmitter> sessionEmitters = emitters.remove(sessionId);
        if (sessionEmitters != null) {
            sessionEmitters.forEach(SseEmitter::complete);
        }
    }

    private String buildPendingKey(long userId, String deliveryAckId) {
        return userId + ":" + deliveryAckId;
    }

    private static final class PendingDelivery {
        private final long userId;
        private final String deliveryAckId;
        private final Map<String, Object> payload;
        private int retryCount;
        private ScheduledFuture<?> future;

        private PendingDelivery(long userId, String deliveryAckId, Map<String, Object> payload) {
            this.userId = userId;
            this.deliveryAckId = deliveryAckId;
            this.payload = new HashMap<>(payload);
            this.retryCount = 0;
        }

        private long userId() {
            return userId;
        }

        private Map<String, Object> payload() {
            return new HashMap<>(payload);
        }

        @SuppressWarnings("unused")
        private String deliveryAckId() {
            return deliveryAckId;
        }

        private synchronized int incrementRetryCount() {
            retryCount += 1;
            return retryCount;
        }

        private synchronized void replaceFuture(ScheduledFuture<?> nextFuture) {
            if (future != null) {
                future.cancel(false);
            }
            future = nextFuture;
        }

        private synchronized void cancel() {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
        }
    }
}
