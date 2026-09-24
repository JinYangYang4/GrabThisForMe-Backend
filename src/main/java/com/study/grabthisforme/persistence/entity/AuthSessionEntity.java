package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_session", indexes = {
    @Index(name = "idx_auth_session_user_status", columnList = "user_id,status"),
    @Index(name = "uk_auth_session_access_hash", columnList = "access_token_hash", unique = true)
})
public class AuthSessionEntity {
    @Id
    @Column(length = 36)
    public String id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "access_token_hash", nullable = false, length = 64)
    public String accessTokenHash;

    @Column(name = "access_expires_at", nullable = false)
    public Long accessExpiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    public Long refreshExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    public Long absoluteExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public AuthSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 40)
    public AuthSessionRevokedReason revokedReason;

    @Column(name = "replaced_by_session_id", length = 36)
    public String replacedBySessionId;

    @Column(name = "device_name", length = 128)
    public String deviceName;

    @Column(name = "created_at", nullable = false)
    public Long createdAt;

    @Column(name = "last_refreshed_at", nullable = false)
    public Long lastRefreshedAt;

    @Column(name = "revoked_at")
    public Long revokedAt;

    public AuthSessionEntity() {
    }
}
