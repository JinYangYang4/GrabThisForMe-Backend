package com.study.grabthisforme.auth;

import com.study.grabthisforme.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private final AuthProperties authProperties;

    public TokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String issueToken(long userId) {
        long expiresAt = Instant.now().plus(authProperties.tokenExpireHours(), ChronoUnit.HOURS).toEpochMilli();
        String payload = userId + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    public AuthenticatedUser parse(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 40101, "Missing token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 40102, "Invalid token");
        }
        String payload = parts[0] + "." + parts[1];
        if (!sign(payload).equals(parts[2])) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 40103, "Invalid token signature");
        }
        long userId = Long.parseLong(parts[0]);
        long expiresAt = Long.parseLong(parts[1]);
        if (Instant.now().toEpochMilli() > expiresAt) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 40104, "Token expired");
        }
        return new AuthenticatedUser(userId);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                authProperties.tokenSecret().getBytes(StandardCharsets.UTF_8),
                ALGORITHM
            );
            mac.init(secretKeySpec);
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, 50002, "Token service unavailable");
        }
    }
}
