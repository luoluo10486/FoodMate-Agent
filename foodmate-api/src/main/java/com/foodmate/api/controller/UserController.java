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

@RestController
@RequestMapping("/api/users/me")
public class UserController extends AuthenticatedControllerSupport {
    public UserController(UserAccountService accounts) { super(accounts); }

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

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UserResponse(long userId, String username, String email, String nickname, String role, String status) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ProfileRequest(String displayName, String gender, @DecimalMin("30") @DecimalMax("250") BigDecimal heightCm, @DecimalMin("2") @DecimalMax("500") BigDecimal weightKg, String activityLevel, String dietGoal, Integer calorieTarget, Integer proteinTarget) {}
}
