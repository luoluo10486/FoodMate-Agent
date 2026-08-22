package com.foodmate.api.controller.account;

import com.foodmate.api.request.account.AdminExportRequest;
import com.foodmate.api.response.account.AdminExportCreatedResponse;
import com.foodmate.api.response.account.AdminExportDownloadResponse;
import com.foodmate.api.response.account.AdminExportStatusResponse;
import com.foodmate.application.account.service.AdminExportService;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only bounded export endpoints; payloads are already redacted by application queries. */
@RestController
@RequestMapping("/api/admin/exports")
public class AdminExportController extends AuthenticatedControllerSupport {
    private final AdminExportService exports;

    public AdminExportController(UserAccountService accounts, AdminExportService exports) {
        super(accounts);
        this.exports = exports;
    }

    @PostMapping
    public ApiResponse<AdminExportCreatedResponse> request(
            @Valid @RequestBody AdminExportRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        var result =
                exports.request(
                        operator.userId(),
                        UserRole.fromCode(operator.role()),
                        new AdminExportService.Request(
                                body.resource(),
                                body.query(),
                                body.status(),
                                body.visibility(),
                                body.sort(),
                                body.direction(),
                                body.fields()),
                        idempotencyKey);
        return ok(new AdminExportCreatedResponse(result.exportJobId()));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<AdminExportStatusResponse> status(
            @PathVariable long jobId, HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        var result = exports.status(operator.userId(), jobId);
        return ok(
                new AdminExportStatusResponse(
                        result.exportJobId(),
                        result.resource(),
                        result.status(),
                        result.expiresAt(),
                        result.completedAt(),
                        result.downloadConsumedAt(),
                        result.failureCode()));
    }

    @PostMapping("/{jobId}/download")
    public ApiResponse<AdminExportDownloadResponse> download(
            @PathVariable long jobId, HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.ADMIN, UserRole.SUPERADMIN);
        return ok(new AdminExportDownloadResponse(exports.consume(operator.userId(), jobId)));
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
