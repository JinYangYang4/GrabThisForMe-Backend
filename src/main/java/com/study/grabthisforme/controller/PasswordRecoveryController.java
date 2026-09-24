package com.study.grabthisforme.controller;
import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth/password-reset")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    public PasswordRecoveryController(PasswordRecoveryService recovery) {this.recovery=recovery;}
    @PostMapping("/request") public ApiResponse<PasswordRecoveryService.Challenge> request(@Valid @RequestBody Request r) {
        return ApiResponse.success(recovery.request(r.identifier(),r.phone()));
    }
    @PostMapping("/confirm") public ApiResponse<Void> confirm(@Valid @RequestBody Confirm r) {
        recovery.reset(r.challengeId(),r.code(),r.newPassword()); return ApiResponse.success(null);
    }
    public record Request(@NotBlank @Size(max=128) String identifier,@NotBlank @Pattern(regexp="[+]?[1-9][0-9]{6,14}") String phone) {}
    public record Confirm(@NotBlank @Size(max=80) String challengeId,@NotBlank @Pattern(regexp="[0-9]{6}") String code,
        @NotBlank @Size(min=8,max=64) @Pattern(regexp="(?s)(?=.*[A-Za-z])(?=.*[0-9]).+") String newPassword) {}
}
