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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountService service;
    public AuthController(UserAccountService service) { this.service = service; }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return response(service.register(request.username(), request.email(), request.password(), request.nickname()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return response(service.login(request.usernameOrEmail(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestHeader(value = "X-Refresh-Token", required = false) String header, @RequestBody(required = false) RefreshRequest request) {
        String token = header != null ? header : request == null ? null : request.refreshToken();
        return response(service.refresh(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "X-Refresh-Token", required = false) String header, @RequestBody(required = false) RefreshRequest request) {
        service.logout(header != null ? header : request == null ? null : request.refreshToken());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_access").toString()).header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_refresh").toString()).body(ApiResponse.success(null, TraceContextHolder.currentOrNew()));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> response(UserAccountService.AuthResult result) {
        AuthResponse body = new AuthResponse(result.userId(), result.username(), result.role(), result.accessToken(), result.accessExpiresAt(), result.refreshToken(), result.refreshExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("foodmate_access", result.accessToken(), result.accessExpiresAt()).toString())
                .header(HttpHeaders.SET_COOKIE, cookie("foodmate_refresh", result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(ApiResponse.success(body, TraceContextHolder.currentOrNew()));
    }

    private static ResponseCookie cookie(String name, String value, java.time.Instant expiresAt) { return ResponseCookie.from(name, value).httpOnly(true).secure(false).sameSite("Lax").path("/").maxAge(java.time.Duration.between(java.time.Instant.now(), expiresAt)).build(); }
    private static ResponseCookie expiredCookie(String name) { return ResponseCookie.from(name, "").httpOnly(true).secure(false).sameSite("Lax").path("/").maxAge(0).build(); }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Email String email, @NotBlank @Size(min = 8, max = 128) String password, @Size(max = 128) String nickname) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RefreshRequest(@NotBlank String refreshToken) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuthResponse(long userId, String username, String role, String accessToken, java.time.Instant accessExpiresAt, String refreshToken, java.time.Instant refreshExpiresAt) {}
}
