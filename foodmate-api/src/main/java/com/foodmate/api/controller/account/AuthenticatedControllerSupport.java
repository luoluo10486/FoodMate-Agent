package com.foodmate.api.controller.account;

import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public abstract class AuthenticatedControllerSupport {
    protected final UserAccountService accounts;

    protected AuthenticatedControllerSupport(UserAccountService accounts) {
        this.accounts = accounts;
    }

    public UserAccountService.UserRecord user(HttpServletRequest request) {
        if (request.getCookies() != null)
            for (Cookie cookie : request.getCookies()) {
                if ("foodmate_session".equals(cookie.getName()))
                    return accounts.requireSessionUser(cookie.getValue());
            }
        throw new BusinessException(ErrorCode.AUTH_REQUIRED);
    }

    protected UserAccountService.UserRecord requireAnyRole(
            HttpServletRequest request, UserRole... roles) {
        UserAccountService.UserRecord current = user(request);
        for (UserRole role : roles) if (role.code().equals(current.role())) return current;
        throw new BusinessException(ErrorCode.FORBIDDEN, "insufficient role");
    }
}
