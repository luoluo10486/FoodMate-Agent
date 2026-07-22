package com.foodmate.api.filter;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces same-origin requests and a session-bound CSRF token for authenticated mutations. */
@Component
public class CsrfProtectionFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name(), HttpMethod.TRACE.name());
    private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/password-reset/request", "/api/auth/password-reset/confirm");
    private final ObjectProvider<UserAccountService> accounts;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CsrfProtectionFilter(ObjectProvider<UserAccountService> accounts) { this.accounts = accounts; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        UserAccountService service = accounts.getIfAvailable();
        if (service == null || SAFE_METHODS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/") || PUBLIC_PATHS.contains(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String sessionToken = cookie(request, "foodmate_session");
        try {
            service.requireSessionUser(sessionToken);
            requireSameOrigin(request);
            service.requireCsrf(sessionToken, request.getHeader("X-CSRF-Token"));
            chain.doFilter(request, response);
        } catch (com.foodmate.shared.error.BusinessException exception) {
            response.setStatus(exception.errorCode().httpStatus());
            response.setContentType("application/json");
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(exception.errorCode(), exception.getMessage(), Map.of(), TraceContextHolder.currentOrNew()));
        }
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private static void requireSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) return;
        String expected = request.getScheme() + "://" + request.getServerName() + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
        if (!expected.equals(origin)) throw new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.FORBIDDEN, "cross-origin mutation is not allowed");
    }
}
