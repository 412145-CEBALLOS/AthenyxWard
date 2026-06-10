package com.athenyx.backend.security;

import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtUtil jwtUtil;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        userRepository = mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(jwtUtil, userRepository);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(chain);
    }

    @Test
    void missingToken_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/emails/fetch");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void invalidToken_returns401_andDoesNotContinueChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/emails/fetch");
        req.setCookies(new Cookie("athenyx_token", "garbage.jwt.value"));
        when(jwtUtil.isTokenValid("garbage.jwt.value")).thenReturn(false);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Token expired or invalid");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void validToken_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/emails/fetch");
        req.setCookies(new Cookie("athenyx_token", "good.jwt.value"));
        when(jwtUtil.isTokenValid("good.jwt.value")).thenReturn(true);
        when(jwtUtil.getUserId("good.jwt.value")).thenReturn(42L);
        when(jwtUtil.getEmail("good.jwt.value")).thenReturn("u@example.com");
        when(jwtUtil.getRole("good.jwt.value")).thenReturn("PREMIUM");
        when(jwtUtil.getTokenVersion("good.jwt.value")).thenReturn(0L);
        when(userRepository.findTokenVersionById(42L)).thenReturn(Optional.of(0L));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }

    @Test
    void validToken_versionMismatch_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/emails/fetch");
        req.setCookies(new Cookie("athenyx_token", "stale.jwt.value"));
        when(jwtUtil.isTokenValid("stale.jwt.value")).thenReturn(true);
        when(jwtUtil.getUserId("stale.jwt.value")).thenReturn(42L);
        when(jwtUtil.getTokenVersion("stale.jwt.value")).thenReturn(0L);
        when(userRepository.findTokenVersionById(42L)).thenReturn(Optional.of(1L));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("Token revoked");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void bearerHeader_invalidToken_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/emails/fetch");
        req.addHeader("Authorization", "Bearer broken.token.here");
        when(jwtUtil.isTokenValid("broken.token.here")).thenReturn(false);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void invalidToken_onAuthEndpoint_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/refresh");
        req.setRequestURI("/api/auth/refresh");
        req.setCookies(new Cookie("athenyx_token", "expired.jwt.value"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
