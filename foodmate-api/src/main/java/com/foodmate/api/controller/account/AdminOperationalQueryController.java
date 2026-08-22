package com.foodmate.api.controller.account;

import com.foodmate.api.response.account.AdminOperationalQueryResponse;
import com.foodmate.application.account.service.AdminOperationalQueryService;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理后台只读运营查询入口。 */
@RestController
@RequestMapping("/api/admin/queries")
public class AdminOperationalQueryController extends AuthenticatedControllerSupport {
    private final AdminOperationalQueryService queries;

    public AdminOperationalQueryController(
            UserAccountService accounts, AdminOperationalQueryService queries) {
        super(accounts);
        this.queries = queries;
    }

    @GetMapping("/{resource}")
    public ApiResponse<AdminOperationalQueryResponse> query(
            HttpServletRequest request,
            @PathVariable String resource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.OPERATOR, UserRole.SUPERADMIN);
        var result =
                queries.query(
                        resource,
                        new AdminOperationalQueryService.Request(
                                page, size, query, status, visibility, sort, direction));
        return ApiResponse.success(
                AdminOperationalQueryResponse.from(resource, result),
                TraceContextHolder.currentOrNew());
    }
}
