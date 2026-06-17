package com.study.grabthisforme.auth;

import com.study.grabthisforme.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new com.study.grabthisforme.common.ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                40101,
                "Missing bearer token"
            );
        }
        AuthenticatedUser authenticatedUser = tokenService.parse(authorization.substring(7));
        AuthContext.setUserId(authenticatedUser.userId());
        return true;
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
    ) {
        AuthContext.clear();
    }
}
