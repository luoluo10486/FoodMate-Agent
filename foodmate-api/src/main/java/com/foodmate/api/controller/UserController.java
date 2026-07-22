package com.foodmate.api.controller;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/users/me")
public class UserController extends AuthenticatedControllerSupport {
    private final com.foodmate.application.account.PersonalDataService personal;
    public UserController(UserAccountService accounts, org.springframework.beans.factory.ObjectProvider<com.foodmate.application.account.PersonalDataService> personal) { super(accounts); this.personal = personal.getIfAvailable(); }

    @GetMapping
    public ApiResponse<UserResponse> me(jakarta.servlet.http.HttpServletRequest request) {
        UserAccountService.UserRecord user = user(request);
        return ApiResponse.success(new UserResponse(user.userId(), user.username(), user.email(), user.nickname(), user.role(), user.status()), TraceContextHolder.currentOrNew());
    }

    @GetMapping("/profile")
    public ApiResponse<UserAccountService.ProfileRecord> profile(jakarta.servlet.http.HttpServletRequest request) {
        return ApiResponse.success(accounts.profile(user(request).userId()), TraceContextHolder.currentOrNew());
    }

    @PutMapping("/profile")
    public ApiResponse<UserAccountService.ProfileRecord> updateProfile(jakarta.servlet.http.HttpServletRequest servletRequest, @Valid @RequestBody ProfileRequest request) {
        var current = user(servletRequest);
        return ApiResponse.success(accounts.updateProfile(current.userId(), new UserAccountService.ProfileUpdate(request.displayName(), request.gender(), request.heightCm(), request.weightKg(), request.activityLevel(), request.dietGoal(), request.calorieTarget(), request.proteinTarget())), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(jakarta.servlet.http.HttpServletRequest request, @Valid @RequestBody PasswordChangeRequest body) {
        accounts.changePassword(user(request).userId(), body.currentPassword(), body.newPassword());
        return ApiResponse.success(null, TraceContextHolder.currentOrNew());
    }

    @GetMapping("/sessions")
    public ApiResponse<java.util.List<UserAccountService.AuthSessionView>> authSessions(jakarta.servlet.http.HttpServletRequest request) {
        return ApiResponse.success(accounts.listAuthSessions(user(request).userId()), TraceContextHolder.currentOrNew());
    }

    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> revokeSession(jakarta.servlet.http.HttpServletRequest request, @PathVariable long id) {
        accounts.revokeAuthSession(user(request).userId(), id);
        return ApiResponse.success(null, TraceContextHolder.currentOrNew());
    }

    @PostMapping("/sessions/revoke-all")
    public ApiResponse<Void> revokeAllSessions(jakarta.servlet.http.HttpServletRequest request) {
        accounts.revokeAllAuthSessions(user(request).userId());
        return ApiResponse.success(null, TraceContextHolder.currentOrNew());
    }

    @org.springframework.web.bind.annotation.PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public ApiResponse<com.foodmate.application.account.PersonalDataService.Avatar> uploadAvatar(jakarta.servlet.http.HttpServletRequest request, @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        if (personal == null) throw new IllegalStateException("personal data unavailable");
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024 || !java.util.Set.of("image/png", "image/jpeg", "image/webp").contains(file.getContentType())) throw new IllegalArgumentException("unsupported avatar");
        return ApiResponse.success(personal.uploadAvatar(user(request).userId(), file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream()), TraceContextHolder.currentOrNew());
    }

    @DeleteMapping("/avatar")
    public ApiResponse<Void> deleteAvatar(jakarta.servlet.http.HttpServletRequest request) { if (personal == null) throw new IllegalStateException("personal data unavailable"); personal.deleteAvatar(user(request).userId()); return ApiResponse.success(null, TraceContextHolder.currentOrNew()); }

    @PostMapping("/export")
    public ApiResponse<java.util.Map<String, Long>> export(jakarta.servlet.http.HttpServletRequest request) { if (personal == null) throw new IllegalStateException("personal data unavailable"); long id = personal.requestExport(user(request).userId()); return ApiResponse.success(java.util.Map.of("export_job_id", id), TraceContextHolder.currentOrNew()); }

    @PostMapping("/deletion")
    public ApiResponse<java.util.Map<String, Long>> deletion(jakarta.servlet.http.HttpServletRequest request, @Valid @RequestBody DeletionRequest body) { if (personal == null) throw new IllegalStateException("personal data unavailable"); if (!"DELETE_MY_ACCOUNT".equals(body.confirmation())) throw new IllegalArgumentException("confirmation required"); long id = personal.requestDeletion(user(request).userId()); return ApiResponse.success(java.util.Map.of("deletion_job_id", id), TraceContextHolder.currentOrNew()); }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UserResponse(long userId, String username, String email, String nickname, String role, String status) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProfileRequest(String displayName, String gender, @DecimalMin("30") @DecimalMax("250") BigDecimal heightCm, @DecimalMin("2") @DecimalMax("500") BigDecimal weightKg, String activityLevel, String dietGoal, Integer calorieTarget, Integer proteinTarget) {}
    public record PasswordChangeRequest(@jakarta.validation.constraints.NotBlank String currentPassword, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 128) String newPassword) {}
    public record DeletionRequest(@jakarta.validation.constraints.NotBlank String confirmation) {}
}
