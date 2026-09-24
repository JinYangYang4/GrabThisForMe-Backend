package com.study.grabthisforme;

import com.study.grabthisforme.auth.AuthProperties;
import com.study.grabthisforme.auth.TokenService;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.AuthRefreshTokenStatus;
import com.study.grabthisforme.persistence.entity.AuthSessionStatus;
import com.study.grabthisforme.persistence.repository.AuthRefreshTokenRepository;
import com.study.grabthisforme.persistence.repository.AuthSessionRepository;
import com.study.grabthisforme.service.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthSessionFlowTests {
    @Autowired AuthProperties properties;
    @Autowired AuthService auth;
    @Autowired TokenService tokens;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthRefreshTokenRepository refreshTokens;

    @Test
    void storesOnlyTokenHashesAndReplacesThePreviousDeviceSession() throws Exception {
        String accountName = "session_" + UUID.randomUUID();
        var first = auth.register(accountName, "Testpass123", "Session Tester", null, null, "Phone A");
        var firstSession = sessions.findById(first.sessionId()).orElseThrow();

        assertThat(first.accessToken()).startsWith("at_").hasSizeGreaterThanOrEqualTo(46);
        assertThat(first.refreshToken()).startsWith("rt_").hasSizeGreaterThanOrEqualTo(46);
        assertThat(firstSession.accessTokenHash).hasSize(64).isNotEqualTo(first.accessToken())
            .isEqualTo(sha256(first.accessToken()));
        assertThat(refreshTokens.findAllBySessionId(first.sessionId()).getFirst().tokenHash)
            .hasSize(64).isNotEqualTo(first.refreshToken()).isEqualTo(sha256(first.refreshToken()));

        firstSession.accessExpiresAt = 0L;
        sessions.saveAndFlush(firstSession);
        assertThatThrownBy(() -> tokens.parse(first.accessToken()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(40104));

        var second = auth.login(accountName, "Testpass123", "Phone B");
        assertThat(second.sessionId()).isNotEqualTo(first.sessionId());
        assertThat(sessions.findById(first.sessionId()).orElseThrow().status).isEqualTo(AuthSessionStatus.REVOKED);
        assertThat(tokens.parse(second.accessToken()).sessionId()).isEqualTo(second.sessionId());
        assertThatThrownBy(() -> tokens.parse(first.accessToken()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(40108));
    }

    @Test
    void freshServiceInstanceValidatesPersistedTokenWithoutASecret() {
        var login = auth.register(
            "restart_" + UUID.randomUUID(), "Testpass123", "Restart Tester", null, null, "Phone A"
        );
        var restarted = new TokenService(properties, sessions, refreshTokens, event -> {});
        assertThat(restarted.parse(login.accessToken()).sessionId()).isEqualTo(login.sessionId());
        assertThatThrownBy(() -> restarted.parse(login.accessToken() + "tampered"))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(40102));
        assertThatThrownBy(() -> restarted.parse(null))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(40101));
    }

    @Test
    void legacyHmacTokensRequireLoginAgain() throws Exception {
        String account = "legacy_" + UUID.randomUUID();
        var old = auth.register(account, "Testpass123", "Legacy Tester", null, null, "Phone A");
        var session = sessions.findById(old.sessionId()).orElseThrow();
        session.accessTokenHash = legacyHmac(old.accessToken());
        sessions.saveAndFlush(session);
        var refresh = refreshTokens.findAllBySessionId(old.sessionId()).getFirst();
        refresh.tokenHash = legacyHmac(old.refreshToken());
        refreshTokens.saveAndFlush(refresh);

        assertThatThrownBy(() -> tokens.parse(old.accessToken()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(40102));
        assertThatThrownBy(() -> auth.refresh(old.refreshToken()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(40114));
        var current = auth.login(account, "Testpass123", "Phone A");
        assertThat(tokens.parse(current.accessToken()).userId()).isEqualTo(old.user().id());
        assertThat(sessions.findById(old.sessionId()).orElseThrow().status).isEqualTo(AuthSessionStatus.REVOKED);
    }

    @Test
    void expiryConfigurationStillRequiresPositiveValues() {
        assertThatThrownBy(() -> new AuthProperties(0, 2592000, 90, 30))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static String sha256(String token) throws Exception {
        return java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
    }

    private static String legacyHmac(String token) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            "legacy-test-secret-at-least-32-characters".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "HmacSHA256"
        ));
        return java.util.HexFormat.of().formatHex(mac.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rotatesRefreshTokensAndRevokesTheSessionWhenAnOldTokenIsReused() {
        var login = auth.register(
            "rotation_" + UUID.randomUUID(), "Testpass123", "Rotation Tester", null, null, "Phone A"
        );
        var refreshed = auth.refresh(login.refreshToken());

        assertThat(refreshed.accessToken()).isNotEqualTo(login.accessToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(tokens.parse(refreshed.accessToken()).userId()).isEqualTo(login.user().id());
        assertThat(refreshTokens.findAllBySessionId(login.sessionId()))
            .extracting(token -> token.status)
            .containsExactlyInAnyOrder(AuthRefreshTokenStatus.USED, AuthRefreshTokenStatus.ACTIVE);

        assertThatThrownBy(() -> auth.refresh(login.refreshToken()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(40112));
        assertThat(sessions.findById(login.sessionId()).orElseThrow().status).isEqualTo(AuthSessionStatus.REVOKED);
        assertThatThrownBy(() -> tokens.parse(refreshed.accessToken()))
            .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(40112));
    }
}
