package com.foodmate.api.controller.account;

import com.foodmate.api.response.account.AdminAuditReportResponse;
import com.foodmate.application.account.service.AdminAuditReportService;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台只读运营审计报告入口。 */
@RestController
@RequestMapping("/api/admin/audit-reports")
public class AdminAuditReportController extends AuthenticatedControllerSupport {
    private final AdminAuditReportService reports;

    public AdminAuditReportController(
            UserAccountService accounts, AdminAuditReportService reports) {
        super(accounts);
        this.reports = reports;
    }

    @GetMapping("/current")
    public ApiResponse<AdminAuditReportResponse> current(HttpServletRequest request) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.OPERATOR, UserRole.SUPERADMIN);
        return ApiResponse.success(
                AdminAuditReportResponse.from(reports.current()),
                TraceContextHolder.currentOrNew());
    }
}
