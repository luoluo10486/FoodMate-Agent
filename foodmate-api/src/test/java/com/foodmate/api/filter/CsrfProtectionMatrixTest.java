package com.foodmate.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.error.BusinessException;
import jakarta.servlet.FilterChain;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfProtectionMatrixTest {
    @Test
    void sameOriginMutationWithValidCsrfContinues() throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("POST", "https://app.example.test", "https://app.example.test", "csrf-token");
        request.setCookies(new jakarta.servlet.http.Cookie("foodmate_session", "session-token"));
        new CsrfProtectionFilter(provider(service)).doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(request, org.mockito.ArgumentMatchers.any());
    }

    @Test
    void crossOriginMutationIsRejectedBeforeChain() throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("POST", "https://app.example.test", "https://evil.example.test", "csrf-token");
        request.setCookies(new jakarta.servlet.http.Cookie("foodmate_session", "session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CsrfProtectionFilter(provider(service)).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void differentPortIsRejected() throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("POST", "https://app.example.test:8443", "https://app.example.test:9443", "csrf-token");
        request.setCookies(new jakarta.servlet.http.Cookie("foodmate_session", "session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CsrfProtectionFilter(provider(service)).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void missingOrInvalidCsrfIsRejected() throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        doThrow(new BusinessException(com.foodmate.shared.error.ErrorCode.FORBIDDEN, "CSRF token is required"))
                .when(service).requireCsrf("session-token", null);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("POST", "http://localhost:8080", null, null);
        request.setCookies(new jakarta.servlet.http.Cookie("foodmate_session", "session-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CsrfProtectionFilter(provider(service)).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void safeMethodsBypassCsrfChecks() throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = request("GET", "https://app.example.test", "https://evil.example.test", null);
        new CsrfProtectionFilter(provider(service)).doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(request, org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(service);
    }

    private static MockHttpServletRequest request(String method, String server, String origin, String csrf) {
        java.net.URI uri = java.net.URI.create(server);
        MockHttpServletRequest request = new MockHttpServletRequest("POST".equals(method) ? "POST" : method, "/api/sessions");
        request.setScheme(uri.getScheme());
        request.setServerName(uri.getHost());
        request.setServerPort(uri.getPort() < 0 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort());
        if (origin != null) request.addHeader("Origin", origin);
        if (csrf != null) request.addHeader("X-CSRF-Token", csrf);
        return request;
    }

    private static ObjectProvider<UserAccountService> provider(UserAccountService service) {
        return new ObjectProvider<>() {
            public UserAccountService getObject(Object... args) { return service; }
            public UserAccountService getIfAvailable() { return service; }
            public UserAccountService getIfUnique() { return service; }
            public Stream<UserAccountService> orderedStream() { return Stream.of(service); }
            public Stream<UserAccountService> stream() { return Stream.of(service); }
        };
    }
}
