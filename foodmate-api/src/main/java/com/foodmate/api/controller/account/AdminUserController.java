package com.foodmate.api.controller.account;

import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController extends AuthenticatedControllerSupport {
    public AdminUserController(UserAccountService accounts) {
        super(accounts);
    }

    @GetMapping
    public ApiResponse<List<UserAccountService.AdminUserView>> list(HttpServletRequest request) {
        requireAnyRole(request, UserRole.ADMIN, UserRole.OPERATOR, UserRole.SUPERADMIN);
        return ApiResponse.success(accounts.listUsersForAdmin(), TraceContextHolder.currentOrNew());
    }
}
