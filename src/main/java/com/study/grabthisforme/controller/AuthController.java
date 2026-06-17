package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.AuthService;
import com.study.grabthisforme.service.view.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthService.AuthResult> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(
            request.accountName(),
            request.password(),
            request.displayName(),
            request.phone(),
            request.email()
        ));
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.AuthResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request.identifier(), request.password()));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.success(authService.me(AuthContext.requireUserId()));
    }

    public record RegisterRequest(
        @NotBlank(message = "accountName is required") String accountName,
        @NotBlank(message = "password is required") String password,
        String displayName,
        String phone,
        String email
    ) {
    }

    public record LoginRequest(
        @NotBlank(message = "identifier is required") String identifier,
        @NotBlank(message = "password is required") String password
    ) {
    }
}
