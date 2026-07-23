package com.foodmate.api.controller;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountService service;
    private final boolean secureCookie;
    private final JavaMailSender mailSender;
    @Value("${spring.mail.username:}") private String mailFrom;
    @Value("${foodmate.web.base-url:http://localhost:5173}") private String webBaseUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(UserAccountService service, @Value("${foodmate.security.cookie-secure:true}") boolean secureCookie,
                          org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailProvider) {
        this.service = service;
        this.secureCookie = secureCookie;
        this.mailSender = mailProvider.getIfAvailable();
    }

    public AuthController(UserAccountService service, boolean secureCookie) {
        this.service = service;
        this.secureCookie = secureCookie;
        this.mailSender = null;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(jakarta.servlet.http.HttpServletRequest request, @Valid @RequestBody RegisterRequest body) {
        return response(service.register(body.username(), body.email(), body.password(), body.nickname(), metadata(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(jakarta.servlet.http.HttpServletRequest request, @Valid @RequestBody LoginRequest body) {
        return response(service.login(body.usernameOrEmail(), body.password(), metadata(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@CookieValue(value = "foodmate_session", required = false) String sessionToken) {
        service.logout(sessionToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_session", true).toString())
                .header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_csrf", false).toString())
                .header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_access", true).toString())
                .header(HttpHeaders.SET_COOKIE, expiredCookie("foodmate_refresh", true).toString())
                .body(ApiResponse.success(null, TraceContextHolder.currentOrNew()));
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestReset(@Valid @RequestBody PasswordResetRequest body) {
        String token = service.createPasswordResetToken(body.email());
        if (mailSender != null && mailFrom != null && !mailFrom.isBlank()) sendResetEmail(body.email(), token);
        return ApiResponse.success(null, TraceContextHolder.currentOrNew());
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest body) {
        service.resetPassword(body.token(), body.newPassword());
        return ApiResponse.success(null, TraceContextHolder.currentOrNew());
    }

    private void sendResetEmail(String recipient, String token) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String url = webBaseUrl.replaceAll("/$", "") + "/reset-password?token=" + token;
            helper.setFrom(mailFrom);
            helper.setTo(recipient);
            helper.setSubject("FoodMate-重置密码");
            helper.setText(textBody(token, url), htmlBody(token, url));
            mailSender.send(message);
        } catch (jakarta.mail.MessagingException exception) {
            throw new IllegalStateException("password reset email unavailable", exception);
        }
    }

    private static String textBody(String token, String url) {
        return "你好，\n\n我们收到了一份重置 FoodMate 密码的请求。别担心，我们来帮你处理。\n"
                + "请打开下面的链接，按页面提示设置一个新的密码：\n" + url
                + "\n\n如果按钮无法打开，也可以使用这串一次性令牌：\n" + token
                + "\n\n链接和令牌 15 分钟内有效，并且只能使用一次。\n如果这不是你发起的请求，直接忽略这封邮件即可，你的账号不会受到影响。\n\n祝你使用愉快，\nFoodMate 团队";
    }

    private static String htmlBody(String token, String url) {
        return "<div style='margin:0;background:#f4f7fb;padding:32px 16px;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Microsoft YaHei,sans-serif;color:#172033'>"
                + "<div style='max-width:600px;margin:auto;background:#fff;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden'>"
                + "<div style='background:#1677ff;padding:24px 32px;color:#fff'><div style='font-size:24px;font-weight:700'>FoodMate</div><div style='margin-top:6px;font-size:13px;opacity:.9'>智能饮食与生活助手</div></div>"
                + "<div style='padding:32px'><div style='font-size:22px;font-weight:700'>你好，帮你把密码重置好</div>"
                + "<p style='margin:18px 0;color:#526076;line-height:1.8'>我们收到了你的密码重置请求。点击下面的按钮，就可以设置一个新的登录密码了。</p>"
                + "<a href='" + url + "' style='display:inline-block;background:#1677ff;color:#fff;text-decoration:none;border-radius:7px;padding:12px 24px;font-weight:600'>去重置密码</a>"
                + "<div style='margin-top:26px;padding:16px;background:#f6f8fb;border-radius:8px;color:#526076;font-size:13px;line-height:1.8'>按钮打不开？没关系，请复制下面的一次性令牌：<br><span style='font-family:Consolas,monospace;color:#172033;word-break:break-all'>" + token + "</span></div>"
                + "<p style='margin:22px 0 0;color:#7b8798;font-size:13px;line-height:1.8'>链接和令牌 15 分钟内有效，并且只能使用一次。如果这不是你发起的请求，忽略这封邮件即可，你的账号不会受到影响。FoodMate 不会通过邮件向你索要密码。</p></div>"
                + "<div style='border-top:1px solid #edf0f4;padding:16px 32px;color:#98a2b3;font-size:12px'>希望 FoodMate 能一直陪你吃得更好、生活得更轻松。<br>此邮件由系统自动发送，请勿直接回复。</div></div></div>";
    }

    private ResponseEntity<ApiResponse<AuthResponse>> response(UserAccountService.AuthResult result) {
        AuthResponse body = new AuthResponse(result.userId(), result.username(), result.role(), result.expiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("foodmate_session", result.sessionToken(), result.expiresAt(), true).toString())
                .header(HttpHeaders.SET_COOKIE, cookie("foodmate_csrf", result.csrfToken(), result.expiresAt(), false).toString())
                .body(ApiResponse.success(body, TraceContextHolder.currentOrNew()));
    }

    private static UserAccountService.SessionMetadata metadata(jakarta.servlet.http.HttpServletRequest request) {
        return new UserAccountService.SessionMetadata(request.getHeader("User-Agent"), request.getRemoteAddr());
    }

    private ResponseCookie cookie(String name, String value, java.time.Instant expiresAt, boolean httpOnly) {
        return ResponseCookie.from(name, value).httpOnly(httpOnly).secure(secureCookie).sameSite("Lax").path("/").maxAge(java.time.Duration.between(java.time.Instant.now(), expiresAt)).build();
    }

    private ResponseCookie expiredCookie(String name, boolean httpOnly) {
        return ResponseCookie.from(name, "").httpOnly(httpOnly).secure(secureCookie).sameSite("Lax").path("/").maxAge(0).build();
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegisterRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 128) String password, @Size(max = 128) String nickname) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password) {}
    public record PasswordResetRequest(@NotBlank @Email String email) {}
    public record PasswordResetConfirmRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 128) String newPassword) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AuthResponse(long userId, String username, String role, java.time.Instant sessionExpiresAt) {}
}
