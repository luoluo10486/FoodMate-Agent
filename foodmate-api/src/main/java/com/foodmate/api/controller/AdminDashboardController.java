package com.foodmate.api.controller;

import com.foodmate.application.account.AdminDashboardService;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
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
    public ApiResponse<Map<String, Object>> dashboard(HttpServletRequest request) {
        requireAnyRole(request, "admin", "operator", "superadmin");
        return ApiResponse.success(dashboard.dashboard(), TraceContextHolder.currentOrNew());
    }
}
