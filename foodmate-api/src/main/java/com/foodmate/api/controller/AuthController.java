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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountService service;
    private final boolean secureCookie;
    public AuthController(UserAccountService service, @Value("${foodmate.security.cookie-secure:true}") boolean secureCookie) { this.service = service; this.secureCookie = secureCookie; }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(jakarta.servlet.http.HttpServletRequest servletRequest, @Valid @RequestBody RegisterRequest request) {
        return response(service.register(request.username(), request.email(), request.password(), request.nickname(), metadata(servletRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(jakarta.servlet.http.HttpServletRequest servletRequest, @Valid @RequestBody LoginRequest request) {
        return response(service.login(request.usernameOrEmail(), request.password(), metadata(servletRequest)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@org.springframework.web.bind.annotation.CookieValue(value = "foodmate_session", required = false) String sessionToken) {
        service.logout(sessionToken);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_session", true).toString()).header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_csrf", false).toString()).header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_access", true).toString()).header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_refresh", true).toString()).body(ApiResponse.success(null, TraceContextHolder.currentOrNew()));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> response(UserAccountService.AuthResult result) {
        AuthResponse body = new AuthResponse(result.userId(), result.username(), result.role(), result.expiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("foodmate_session", result.sessionToken(), result.expiresAt(), true).toString())
                .header(HttpHeaders.SET_COOKIE, cookie("foodmate_csrf", result.csrfToken(), result.expiresAt(), false).toString())
                .body(ApiResponse.success(body, TraceContextHolder.currentOrNew()));
    }

    private static UserAccountService.SessionMetadata metadata(jakarta.servlet.http.HttpServletRequest request) { return new UserAccountService.SessionMetadata(request.getHeader("User-Agent"), request.getRemoteAddr()); }

    private ResponseCookie cookie(String name, String value, java.time.Instant expiresAt, boolean httpOnly) { return ResponseCookie.from(name, value).httpOnly(httpOnly).secure(secureCookie).sameSite("Lax").path("/").maxAge(java.time.Duration.between(java.time.Instant.now(), expiresAt)).build(); }
    private ResponseCookie expiredCookie(String name, boolean httpOnly) { return ResponseCookie.from(name, "").httpOnly(httpOnly).secure(secureCookie).sameSite("Lax").path("/").maxAge(0).build(); }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Email String email, @NotBlank @Size(min = 8, max = 128) String password, @Size(max = 128) String nickname) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuthResponse(long userId, String username, String role, java.time.Instant sessionExpiresAt) {}
}
