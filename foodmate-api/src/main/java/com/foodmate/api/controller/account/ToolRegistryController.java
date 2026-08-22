package com.foodmate.api.controller.account;

import com.foodmate.api.response.account.ToolRegistryResponse;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.ToolRegistryService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端只读查看已发布工具注册信息。 */
@RestController
@RequestMapping("/api/admin/tools/registry")
public class ToolRegistryController extends AuthenticatedControllerSupport {
    private final ToolRegistryService registry;

    public ToolRegistryController(UserAccountService accounts, ToolRegistryService registry) {
        super(accounts);
        this.registry = registry;
    }

    @GetMapping
    public ApiResponse<ToolRegistryResponse> list(HttpServletRequest request) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.OPERATOR, UserRole.SUPERADMIN);
        return ApiResponse.success(
                ToolRegistryResponse.from(registry.list()), TraceContextHolder.currentOrNew());
    }
}
