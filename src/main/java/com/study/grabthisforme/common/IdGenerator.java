package com.study.grabthisforme.common;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    private final AtomicLong numericId = new AtomicLong(System.currentTimeMillis());

    public long nextLongId() {
        return numericId.incrementAndGet();
    }

    public String nextConversationId() {
        return UUID.randomUUID().toString();
    }

    public String nextMessageId() {
        return "MSG_" + UUID.randomUUID();
    }

    public String nextOrderId() {
        return "ORD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    public String nextPostId() {
        return "POST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
