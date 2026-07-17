package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;

abstract class AuthenticatedControllerSupport {
    protected final UserAccountService accounts;
    protected AuthenticatedControllerSupport(UserAccountService accounts) { this.accounts = accounts; }

    protected UserAccountService.UserRecord user(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        return accounts.requireUser(authorization.substring(7));
    }
}
