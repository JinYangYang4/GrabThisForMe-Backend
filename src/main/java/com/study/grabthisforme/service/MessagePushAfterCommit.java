package com.study.grabthisforme.service;

import java.util.List;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class MessagePushAfterCommit {
    static void send(PushService push, String conversationId, List<Long> members, Object payload) {
        Runnable send = () -> { try { push.pushConversationMessage(conversationId, members, payload); }
            catch (RuntimeException ignored) { /* HTTP/history remain authoritative; reconnect catches up. */ } };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        else send.run();
    }
}
