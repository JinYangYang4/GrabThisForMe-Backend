package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_refresh_token", indexes = {
    @Index(name = "uk_auth_refresh_hash", columnList = "token_hash", unique = true),
    @Index(name = "idx_auth_refresh_session_generation", columnList = "session_id,generation")
})
public class AuthRefreshTokenEntity {
    @Id
    @Column(length = 36)
    public String id;

    @Column(name = "session_id", nullable = false, length = 36)
    public String sessionId;

    @Column(name = "token_hash", nullable = false, length = 64)
    public String tokenHash;

    @Column(nullable = false)
    public Integer generation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public AuthRefreshTokenStatus status;

    @Column(name = "created_at", nullable = false)
    public Long createdAt;

    @Column(name = "expires_at", nullable = false)
    public Long expiresAt;

    @Column(name = "used_at")
    public Long usedAt;

    @Column(name = "replaced_by_id", length = 36)
    public String replacedById;

    public AuthRefreshTokenEntity() {
    }
}
