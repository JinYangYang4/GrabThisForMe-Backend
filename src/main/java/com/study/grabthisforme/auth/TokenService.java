package com.study.grabthisforme.auth;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.AuthRefreshTokenEntity;
import com.study.grabthisforme.persistence.entity.AuthRefreshTokenStatus;
import com.study.grabthisforme.persistence.entity.AuthSessionEntity;
import com.study.grabthisforme.persistence.entity.AuthSessionRevokedReason;
import com.study.grabthisforme.persistence.entity.AuthSessionStatus;
import com.study.grabthisforme.persistence.repository.AuthRefreshTokenRepository;
import com.study.grabthisforme.persistence.repository.AuthSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TokenService {
    private static final int TOKEN_RANDOM_BYTES = 32;

    private final AuthProperties properties;
    private final AuthSessionRepository sessions;
    private final AuthRefreshTokenRepository refreshTokens;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenService(
        AuthProperties properties,
        AuthSessionRepository sessions,
        AuthRefreshTokenRepository refreshTokens,
        ApplicationEventPublisher eventPublisher
    ) {
        this.properties = properties;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.eventPublisher = eventPublisher;
    }

    public SessionTokens createSession(long userId, String deviceName) {
        long now = System.currentTimeMillis();
        long absoluteExpiresAt = now + daysToMillis(properties.sessionAbsoluteExpireDays());
        long refreshExpiresAt = Math.min(absoluteExpiresAt, now + secondsToMillis(properties.refreshTokenExpireSeconds()));
        String accessToken = randomToken("at_");
        String refreshToken = randomToken("rt_");

        AuthSessionEntity session = new AuthSessionEntity();
        session.id = UUID.randomUUID().toString();
        session.userId = userId;
        session.accessTokenHash = hash(accessToken);
        session.accessExpiresAt = Math.min(
            absoluteExpiresAt,
            now + secondsToMillis(properties.accessTokenExpireSeconds())
        );
        session.refreshExpiresAt = refreshExpiresAt;
        session.absoluteExpiresAt = absoluteExpiresAt;
        session.status = AuthSessionStatus.ACTIVE;
        session.deviceName = normalizeDeviceName(deviceName);
        session.createdAt = now;
        session.lastRefreshedAt = now;
        sessions.save(session);

        saveRefreshToken(session.id, refreshToken, 1, now, refreshExpiresAt);
        return toTokens(session, accessToken, refreshToken, now);
    }

    public SessionTokens replaceActiveSession(long userId, String deviceName) {
        List<AuthSessionEntity> oldSessions = sessions.findActiveForUpdate(userId);
        SessionTokens replacement = createSession(userId, deviceName);
        List<String> revokedSessionIds = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (AuthSessionEntity oldSession : oldSessions) {
            revoke(oldSession, AuthSessionRevokedReason.REPLACED_BY_NEW_LOGIN, now);
            oldSession.replacedBySessionId = replacement.sessionId();
            sessions.save(oldSession);
            revokedSessionIds.add(oldSession.id);
        }
        pushRevocationsAfterCommit(revokedSessionIds, AuthSessionRevokedReason.REPLACED_BY_NEW_LOGIN);
        return replacement;
    }

    public AuthenticatedUser authenticateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized(40101, "Missing token");
        }
        AuthSessionEntity session = sessions.findByAccessTokenHash(hash(token))
            .orElseThrow(() -> unauthorized(40102, "Invalid token"));
        ensureActive(session);
        long now = System.currentTimeMillis();
        if (now > session.absoluteExpiresAt) {
            throw unauthorized(40111, "Session expired");
        }
        if (now > session.accessExpiresAt) {
            throw unauthorized(40104, "Access token expired");
        }
        return new AuthenticatedUser(session.userId, session.id);
    }

    /** Source-compatible alias for the existing HTTP and WebSocket interceptors. */
    public AuthenticatedUser parse(String token) {
        return authenticateAccessToken(token);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SessionTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw unauthorized(40114, "Invalid refresh token");
        }
        AuthRefreshTokenEntity current = refreshTokens.findByTokenHashForUpdate(hash(refreshToken))
            .orElseThrow(() -> unauthorized(40114, "Invalid refresh token"));
        AuthSessionEntity session = sessions.findForUpdate(current.sessionId)
            .orElseThrow(() -> unauthorized(40114, "Invalid refresh token"));

        ensureActive(session);
        long now = System.currentTimeMillis();
        if (current.status == AuthRefreshTokenStatus.USED) {
            revoke(session, AuthSessionRevokedReason.REFRESH_TOKEN_REUSED, now);
            sessions.saveAndFlush(session);
            pushRevocationsAfterCommit(List.of(session.id), AuthSessionRevokedReason.REFRESH_TOKEN_REUSED);
            throw unauthorized(40112, "Refresh token reused");
        }
        if (current.status != AuthRefreshTokenStatus.ACTIVE) {
            throw unauthorized(40114, "Invalid refresh token");
        }
        if (now > current.expiresAt || now > session.refreshExpiresAt || now > session.absoluteExpiresAt) {
            current.status = AuthRefreshTokenStatus.REVOKED;
            current.usedAt = now;
            refreshTokens.save(current);
            session.status = AuthSessionStatus.EXPIRED;
            session.revokedAt = now;
            sessions.saveAndFlush(session);
            throw unauthorized(40111, "Refresh token expired");
        }

        String nextAccessToken = randomToken("at_");
        String nextRefreshToken = randomToken("rt_");
        long nextRefreshExpiresAt = Math.min(session.absoluteExpiresAt, now + secondsToMillis(properties.refreshTokenExpireSeconds()));
        AuthRefreshTokenEntity next = saveRefreshToken(
            session.id,
            nextRefreshToken,
            current.generation + 1,
            now,
            nextRefreshExpiresAt
        );
        current.status = AuthRefreshTokenStatus.USED;
        current.usedAt = now;
        current.replacedById = next.id;
        refreshTokens.save(current);

        session.accessTokenHash = hash(nextAccessToken);
        session.accessExpiresAt = Math.min(
            session.absoluteExpiresAt,
            now + secondsToMillis(properties.accessTokenExpireSeconds())
        );
        session.refreshExpiresAt = nextRefreshExpiresAt;
        session.lastRefreshedAt = now;
        sessions.saveAndFlush(session);
        return toTokens(session, nextAccessToken, nextRefreshToken, now);
    }

    public SessionTokens rotateCurrentSession(long userId, String sessionId) {
        AuthSessionEntity session = sessions.findForUpdate(sessionId)
            .orElseThrow(() -> unauthorized(40102, "Invalid session"));
        if (session.userId != userId) {
            throw unauthorized(40102, "Invalid session owner");
        }
        ensureActive(session);
        long now = System.currentTimeMillis();
        revokeActiveRefreshTokens(session.id, now);

        String accessToken = randomToken("at_");
        String refreshToken = randomToken("rt_");
        long refreshExpiresAt = Math.min(session.absoluteExpiresAt, now + secondsToMillis(properties.refreshTokenExpireSeconds()));
        int nextGeneration = refreshTokens.findTopBySessionIdOrderByGenerationDesc(session.id)
            .map(token -> token.generation + 1)
            .orElse(1);
        saveRefreshToken(session.id, refreshToken, nextGeneration, now, refreshExpiresAt);
        session.accessTokenHash = hash(accessToken);
        session.accessExpiresAt = Math.min(
            session.absoluteExpiresAt,
            now + secondsToMillis(properties.accessTokenExpireSeconds())
        );
        session.refreshExpiresAt = refreshExpiresAt;
        session.lastRefreshedAt = now;
        sessions.save(session);
        return toTokens(session, accessToken, refreshToken, now);
    }

    public SessionTokens rotateActiveSession(long userId) {
        AuthSessionEntity session = sessions.findActiveForUpdate(userId).stream()
            .findFirst()
            .orElseThrow(() -> unauthorized(40102, "Invalid session"));
        return rotateCurrentSession(userId, session.id);
    }

    public void revokeCurrentSession(long userId, String sessionId, AuthSessionRevokedReason reason, boolean push) {
        AuthSessionEntity session = sessions.findForUpdate(sessionId)
            .orElseThrow(() -> unauthorized(40102, "Invalid session"));
        if (session.userId != userId) {
            throw unauthorized(40102, "Invalid session owner");
        }
        if (session.status == AuthSessionStatus.ACTIVE) {
            revoke(session, reason, System.currentTimeMillis());
            sessions.save(session);
            if (push) pushRevocationsAfterCommit(List.of(session.id), reason);
        }
    }

    public void revokeAllSessions(long userId, AuthSessionRevokedReason reason, boolean push) {
        List<AuthSessionEntity> activeSessions = sessions.findActiveForUpdate(userId);
        long now = System.currentTimeMillis();
        List<String> ids = new ArrayList<>();
        for (AuthSessionEntity session : activeSessions) {
            revoke(session, reason, now);
            sessions.save(session);
            ids.add(session.id);
        }
        if (push) pushRevocationsAfterCommit(ids, reason);
    }

    public boolean isSessionActive(String sessionId, long userId) {
        long now = System.currentTimeMillis();
        return sessions.findById(sessionId)
            .map(session -> session.userId == userId
                && session.status == AuthSessionStatus.ACTIVE
                && now <= session.absoluteExpiresAt)
            .orElse(false);
    }

    private AuthRefreshTokenEntity saveRefreshToken(
        String sessionId,
        String rawToken,
        int generation,
        long now,
        long expiresAt
    ) {
        AuthRefreshTokenEntity entity = new AuthRefreshTokenEntity();
        entity.id = UUID.randomUUID().toString();
        entity.sessionId = sessionId;
        entity.tokenHash = hash(rawToken);
        entity.generation = generation;
        entity.status = AuthRefreshTokenStatus.ACTIVE;
        entity.createdAt = now;
        entity.expiresAt = expiresAt;
        return refreshTokens.save(entity);
    }

    private void revoke(AuthSessionEntity session, AuthSessionRevokedReason reason, long now) {
        session.status = AuthSessionStatus.REVOKED;
        session.revokedReason = reason;
        session.revokedAt = now;
        revokeActiveRefreshTokens(session.id, now);
    }

    private void revokeActiveRefreshTokens(String sessionId, long now) {
        List<AuthRefreshTokenEntity> active = refreshTokens.findAllBySessionIdAndStatus(
            sessionId,
            AuthRefreshTokenStatus.ACTIVE
        );
        for (AuthRefreshTokenEntity token : active) {
            token.status = AuthRefreshTokenStatus.REVOKED;
            token.usedAt = now;
        }
        refreshTokens.saveAll(active);
    }

    private void ensureActive(AuthSessionEntity session) {
        if (session.status == AuthSessionStatus.ACTIVE) return;
        if (session.status == AuthSessionStatus.EXPIRED) {
            throw unauthorized(40111, "Session expired");
        }
        if (session.revokedReason == null) {
            throw unauthorized(40102, "Session revoked");
        }
        int code = switch (session.revokedReason) {
            case REPLACED_BY_NEW_LOGIN -> 40108;
            case USER_LOGOUT -> 40109;
            case PASSWORD_CHANGED, PASSWORD_RESET -> 40110;
            case ADMIN_REVOKED -> 40113;
            case REFRESH_TOKEN_REUSED, SECURITY_RISK -> 40112;
        };
        throw unauthorized(code, "Session revoked");
    }

    private SessionTokens toTokens(AuthSessionEntity session, String accessToken, String refreshToken, long now) {
        return new SessionTokens(
            accessToken,
            Math.max(0L, (session.accessExpiresAt - now) / 1000L),
            refreshToken,
            Math.max(0L, (session.refreshExpiresAt - now) / 1000L),
            session.id
        );
    }

    private String randomToken(String prefix) {
        byte[] bytes = new byte[TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            // Tokens contain 256 bits of secure randomness; store only their digest.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, 50002, "Token service unavailable");
        }
    }

    private String normalizeDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) return "Unknown Android device";
        String normalized = deviceName.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private void pushRevocationsAfterCommit(List<String> sessionIds, AuthSessionRevokedReason reason) {
        if (sessionIds.isEmpty()) return;
        Runnable action = () -> sessionIds.forEach(
            sessionId -> eventPublisher.publishEvent(new AuthSessionRevokedEvent(sessionId, reason))
        );
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private long secondsToMillis(long seconds) {
        return Math.multiplyExact(seconds, 1000L);
    }

    private long daysToMillis(long days) {
        return Math.multiplyExact(days, 86_400_000L);
    }

    private ApiException unauthorized(int code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public record SessionTokens(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn,
        String sessionId
    ) {
    }
}
