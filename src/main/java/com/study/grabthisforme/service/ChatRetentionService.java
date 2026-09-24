package com.study.grabthisforme.service;

import com.study.grabthisforme.persistence.entity.MediaEntity;
import com.study.grabthisforme.persistence.repository.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Server retention, not a client delete/tombstone event: local message history stays intact. */
@Service
public class ChatRetentionService {
    private static final Logger log = LoggerFactory.getLogger(ChatRetentionService.class);
    private static final int PAGE_SIZE = 100;
    private final MessageRepository messages;
    private final MediaRepository media;
    private final ConversationRepository conversations;
    private final ConversationUserStateRepository states;
    private final MediaService files;
    private final TransactionTemplate transaction;
    private final long retentionMillis;
    private final boolean enabled;

    public ChatRetentionService(MessageRepository messages, MediaRepository media,
        ConversationRepository conversations, ConversationUserStateRepository states,
        MediaService files, PlatformTransactionManager manager,
        @Value("${grabthisforme.chat.retention-days:30}") long days,
        @Value("${grabthisforme.chat.cleanup-enabled:true}") boolean enabled) {
        if (days < 1 || days > 36500) throw new IllegalArgumentException("Chat retention days must be 1..36500");
        this.messages = messages; this.media = media; this.conversations = conversations;
        this.states = states; this.files = files; this.enabled = enabled;
        retentionMillis = Math.multiplyExact(days, 86_400_000L);
        transaction = new TransactionTemplate(manager);
        // Marking must commit before deleting bytes, even when invoked by another transaction.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${grabthisforme.chat.cleanup-interval-ms:3600000}",
        initialDelayString = "${grabthisforme.chat.cleanup-initial-delay-ms:60000}")
    public void cleanup() {
        if (enabled) cleanupAt(System.currentTimeMillis());
    }

    public record Result(long messagesDeleted, long mediaDeleted, long failures) {}

    /** Explicit clock for boundary regression tests. No HTTP endpoint exposes this operation. */
    public synchronized Result cleanupAt(long now) {
        long cutoff = Math.subtractExact(now, retentionMillis);
        long messageCount = 0, mediaCount = 0, failures = 0;
        String cursor = null;
        while (true) {
            List<String> ids = messages.findExpiredConversationIds(cutoff, cursor, PageRequest.of(0, PAGE_SIZE));
            if (ids.isEmpty()) break;
            for (String id : ids) {
                try { messageCount += Objects.requireNonNull(transaction.execute(status -> expireMessages(id, cutoff))); }
                catch (RuntimeException error) { failures++; reportFailure(error); }
            }
            cursor = ids.getLast(); // Keyset pagination remains correct while rows are deleted.
        }
        cursor = null;
        while (true) {
            List<String> ids = media.findRetentionCandidates(cutoff, cursor, PageRequest.of(0, PAGE_SIZE));
            if (ids.isEmpty()) break;
            for (String id : ids) {
                try {
                    boolean marked = Boolean.TRUE.equals(transaction.execute(status -> markMedia(id, cutoff)));
                    if (marked && Boolean.TRUE.equals(transaction.execute(status -> removeMedia(id)))) mediaCount++;
                } catch (RuntimeException error) { failures++; reportFailure(error); }
            }
            cursor = ids.getLast();
        }
        if (messageCount > 0 || mediaCount > 0 || failures > 0)
            log.info("Chat retention: messages_deleted={}, media_deleted={}, failures={}", messageCount, mediaCount, failures);
        return new Result(messageCount, mediaCount, failures);
    }

    private int expireMessages(String id, long cutoff) {
        // Same lock as sendMessage/upload, so a new last message cannot be overwritten by cleanup.
        var conversation = conversations.findForUpdate(id).orElse(null);
        int count = messages.deleteExpired(id, cutoff);
        if (conversation != null && count > 0) {
            var last = messages.findTopByConversationIdOrderByTimestampDesc(id).orElse(null);
            conversation.lastMessageId = last == null ? null : last.messageId;
            if (last != null) conversation.lastTime = last.timestamp;
            conversations.save(conversation);
            for (var state : states.findAllByConversationId(id)) {
                long remaining = messages.countUnreadRemaining(id, state.userId, state.lastReadTime);
                // Do not resurrect unread counts cleared by an explicit markRead operation.
                state.unreadCount = (int) Math.min(Math.max(0, state.unreadCount == null ? 0 : state.unreadCount), remaining);
                states.save(state);
            }
        }
        return count;
    }

    private MediaEntity lockMediaConversation(String id) {
        var snapshot = media.findById(id).orElse(null);
        if (snapshot == null || snapshot.conversationId == null) return null;
        conversations.findForUpdate(snapshot.conversationId);
        // Reload after waiting for the conversation lock. The first read may be stale in JPA's cache.
        entityRefresh(snapshot);
        return snapshot;
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private void entityRefresh(MediaEntity asset) {
        entityManager.refresh(asset);
    }

    private boolean markMedia(String id, long cutoff) {
        var asset = lockMediaConversation(id);
        if (asset == null) return false;
        if (Boolean.TRUE.equals(asset.deletionPending)) return true;
        Long retainedSince = asset.retentionTime == null ? asset.createdTime : asset.retentionTime;
        if (retainedSince == null || retainedSince >= cutoff || messages.countMediaReferences(id) > 0) return false;
        asset.deletionPending = true;
        media.saveAndFlush(asset);
        return true;
    }

    private boolean removeMedia(String id) {
        var asset = lockMediaConversation(id);
        if (asset == null || !Boolean.TRUE.equals(asset.deletionPending)) return false;
        // A conservative guard for imported/legacy references; normal sends cannot use a tombstone.
        if (messages.countMediaReferences(id) > 0) return false;
        try { files.deleteRetiredFiles(asset); }
        catch (IOException error) { throw new UncheckedIOException(error); }
        media.delete(asset);
        media.flush();
        return true;
    }

    private void reportFailure(RuntimeException error) {
        // Exception messages may contain filesystem paths/SQL. Log only the type and retry next sweep.
        log.warn("Chat retention operation failed; will retry: {}", error.getClass().getSimpleName());
    }
}
