package com.study.grabthisforme.persistence.entity;

public enum AuthSessionRevokedReason {
    REPLACED_BY_NEW_LOGIN,
    USER_LOGOUT,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    ADMIN_REVOKED,
    REFRESH_TOKEN_REUSED,
    SECURITY_RISK
}
