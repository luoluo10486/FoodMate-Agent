package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

abstract class AuthenticatedControllerSupport {
    protected final UserAccountService accounts;
    protected AuthenticatedControllerSupport(UserAccountService accounts) { this.accounts = accounts; }

    protected UserAccountService.UserRecord user(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        return accounts.requireUser(authorization.substring(7));
    }

    protected UserAccountService.UserRecord user(String authorization, HttpServletRequest request) {
        if (authorization != null && authorization.startsWith("Bearer ")) return accounts.requireUser(authorization.substring(7));
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if ("foodmate_access".equals(cookie.getName())) return accounts.requireUser(cookie.getValue());
        }
        throw new BusinessException(ErrorCode.AUTH_REQUIRED);
    }
}
