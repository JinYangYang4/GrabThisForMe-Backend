package com.study.grabthisforme.auth;

public record AuthenticatedUser(long userId, String sessionId) {
}
