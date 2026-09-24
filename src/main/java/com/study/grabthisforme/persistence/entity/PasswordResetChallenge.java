package com.study.grabthisforme.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="password_reset_challenge", indexes=@Index(columnList="phone,createdAt"))
public class PasswordResetChallenge {
    @Id public String challengeId;
    public Long userId;
    public Long tokenVersion;
    public String phone;
    public String codeHash;
    public long createdAt;
    public long expiresAt;
    public int attempts;
    public boolean consumed;
}
