package com.study.grabthisforme.auth;

import com.study.grabthisforme.persistence.entity.AuthSessionRevokedReason;

public record AuthSessionRevokedEvent(String sessionId, AuthSessionRevokedReason reason) {
}
