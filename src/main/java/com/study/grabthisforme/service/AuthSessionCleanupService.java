package com.study.grabthisforme.service;

import com.study.grabthisforme.auth.AuthProperties;
import com.study.grabthisforme.persistence.entity.AuthRefreshTokenStatus;
import com.study.grabthisforme.persistence.entity.AuthSessionStatus;
import com.study.grabthisforme.persistence.repository.AuthRefreshTokenRepository;
import com.study.grabthisforme.persistence.repository.AuthSessionRepository;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionCleanupService {
    private final AuthProperties properties;
    private final AuthSessionRepository sessions;
    private final AuthRefreshTokenRepository refreshTokens;

    public AuthSessionCleanupService(
        AuthProperties properties,
        AuthSessionRepository sessions,
        AuthRefreshTokenRepository refreshTokens
    ) {
        this.properties = properties;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(fixedDelayString = "${grabthisforme.auth.cleanup-interval-ms:21600000}")
    @Transactional
    public void cleanup() {
        long now = System.currentTimeMillis();
        markExpiredSessions(now);

        long retentionCutoff = now - Math.multiplyExact(properties.revokedRetentionDays(), 86_400_000L);
        refreshTokens.deleteAll(
            refreshTokens.findAllByStatusNotAndCreatedAtLessThan(AuthRefreshTokenStatus.ACTIVE, retentionCutoff)
        );
        var removableSessions = sessions.findAllByStatusNotAndRevokedAtLessThan(AuthSessionStatus.ACTIVE, retentionCutoff);
        List<String> removableIds = removableSessions.stream().map(session -> session.id).toList();
        if (!removableIds.isEmpty()) {
            refreshTokens.deleteAllBySessionIdIn(removableIds);
            sessions.deleteAll(removableSessions);
        }
    }

    private void markExpiredSessions(long now) {
        var expired = sessions.findAllByStatusAndAbsoluteExpiresAtLessThan(AuthSessionStatus.ACTIVE, now);
        for (var session : expired) {
            session.status = AuthSessionStatus.EXPIRED;
            session.revokedAt = now;
        }
        sessions.saveAll(expired);
    }
}
