package com.athenyx.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshOriginFilterTest {

    private RefreshOriginFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RefreshOriginFilter();
        ReflectionTestUtils.setField(filter, "frontendUrl", "http://localhost:4200");
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(chain);
    }

    @Test
    void allowsMatchingOrigin() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/refresh");
        req.setRequestURI("/api/auth/refresh");
        req.addHeader("Origin", "http://localhost:4200");
        req.setCookies(new Cookie("athenyx_refresh", "abc"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain).doFilter(req, res);
    }

    @Test
    void rejectsMismatchedOrigin() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/refresh");
        req.setRequestURI("/api/auth/refresh");
        req.addHeader("Origin", "http://evil.example.com");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsMismatchedReferer() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/refresh");
        req.setRequestURI("/api/auth/refresh");
        req.addHeader("Referer", "http://evil.example.com/");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowsMissingOriginAndReferer() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/refresh");
        req.setRequestURI("/api/auth/refresh");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void ignoresOtherPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/logout");
        req.setRequestURI("/api/auth/logout");
        req.addHeader("Origin", "http://evil.example.com");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }
}
