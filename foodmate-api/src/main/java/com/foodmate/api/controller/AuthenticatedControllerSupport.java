package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

abstract class AuthenticatedControllerSupport {
    protected final UserAccountService accounts;
    protected AuthenticatedControllerSupport(UserAccountService accounts) { this.accounts = accounts; }

    protected UserAccountService.UserRecord user(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if ("foodmate_session".equals(cookie.getName())) return accounts.requireSessionUser(cookie.getValue());
        }
        throw new BusinessException(ErrorCode.AUTH_REQUIRED);
    }
}
