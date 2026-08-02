package com.foodmate.api.controller.account;

import com.foodmate.api.response.account.AdminDashboardResponse;
import com.foodmate.application.account.service.AdminDashboardService;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台只负责授权和响应，统计查询由 Application/Infra 层完成。 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController extends AuthenticatedControllerSupport {
    private final AdminDashboardService dashboard;

    public AdminDashboardController(UserAccountService accounts, AdminDashboardService dashboard) {
        super(accounts);
        this.dashboard = dashboard;
    }

    @GetMapping
    public ApiResponse<AdminDashboardResponse> dashboard(HttpServletRequest request) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.OPERATOR, UserRole.SUPERADMIN);
        return ApiResponse.success(
                AdminDashboardResponse.from(dashboard.dashboard()),
                TraceContextHolder.currentOrNew());
    }
}
