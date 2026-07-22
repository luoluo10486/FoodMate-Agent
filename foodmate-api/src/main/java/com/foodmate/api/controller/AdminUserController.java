package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController extends AuthenticatedControllerSupport {
    public AdminUserController(UserAccountService accounts) { super(accounts); }

    @GetMapping
    public ApiResponse<List<UserAccountService.AdminUserView>> list(HttpServletRequest request) {
        requireAnyRole(request, "admin", "operator", "superadmin");
        return ApiResponse.success(accounts.listUsersForAdmin(), TraceContextHolder.currentOrNew());
    }
}
