package com.study.grabthisforme.common;

public final class AuthContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static long requireUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, 40101, "Unauthorized");
        }
        return userId;
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
