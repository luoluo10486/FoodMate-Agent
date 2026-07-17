package com.foodmate.api.controller;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountService service;
    public AuthController(UserAccountService service) { this.service = service; }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return response(service.register(request.username(), request.email(), request.password(), request.nickname()));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return response(service.login(request.usernameOrEmail(), request.password()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return response(service.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "X-Refresh-Token", required = false) String header, @RequestBody(required = false) RefreshRequest request) {
        service.logout(header != null ? header : request == null ? null : request.refreshToken());
        return ApiResponse.success(null, TraceContextHolder.currentOrNew());
    }

    private ApiResponse<AuthResponse> response(UserAccountService.AuthResult result) {
        return ApiResponse.success(new AuthResponse(result.userId(), result.username(), result.role(), result.accessToken(), result.accessExpiresAt(), result.refreshToken(), result.refreshExpiresAt()), TraceContextHolder.currentOrNew());
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Email String email, @NotBlank @Size(min = 8, max = 128) String password, @Size(max = 128) String nickname) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RefreshRequest(@NotBlank String refreshToken) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuthResponse(long userId, String username, String role, String accessToken, java.time.Instant accessExpiresAt, String refreshToken, java.time.Instant refreshExpiresAt) {}
}
