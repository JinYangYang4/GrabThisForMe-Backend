package com.study.grabthisforme.controller;
import com.study.grabthisforme.common.*;
import com.study.grabthisforme.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/users/me")
public class AccountController {
    private final AuthService auth;
    public AccountController(AuthService auth) { this.auth=auth; }
    @PostMapping("/password")
    public ApiResponse<AuthService.AuthResult> password(@Valid @RequestBody PasswordRequest r) {
        return ApiResponse.success(auth.changePassword(
            AuthContext.requireUserId(), AuthContext.requireSessionId(), r.currentPassword(), r.newPassword()
        ));
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        auth.logout(AuthContext.requireUserId(), AuthContext.requireSessionId());
        return ApiResponse.success(null);
    }
    public record PasswordRequest(@NotBlank @Size(max=128) String currentPassword,
        @NotBlank @Size(min=8,max=64) @Pattern(regexp="(?s)(?=.*[A-Za-z])(?=.*[0-9]).+") String newPassword) {}
}
