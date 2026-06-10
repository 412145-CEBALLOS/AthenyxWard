package com.athenyx.backend.security;

import com.athenyx.backend.entity.Role;
import com.athenyx.backend.entity.User;
import com.athenyx.backend.repository.UserRepository;
import com.athenyx.backend.service.RefreshTokenService;
import com.athenyx.backend.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private OAuth2AuthorizedClientService authorizedClientService;
    @Mock private TokenEncryptionService tokenEncryptionService;
    @Mock private RefreshTokenService refreshTokenService;

    private JwtUtil jwtUtil;
    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil("abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJ", 900_000L);
        handler = new OAuth2LoginSuccessHandler(
                userRepository, jwtUtil, authorizedClientService,
                tokenEncryptionService, refreshTokenService);
        setField("frontendUrl", "http://localhost:4200");
        setField("cookieSecure", false);

        lenient().when(tokenEncryptionService.encrypt(any())).thenAnswer(inv -> "enc(" + inv.getArgument(0) + ")");
    }

    private void setField(String name, Object value) throws Exception {
        Field f = OAuth2LoginSuccessHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(handler, value);
    }

    @Test
    void setsBothAccessAndRefreshCookiesOnSuccess() throws Exception {
        User existing = User.builder()
                .id(7L)
                .googleId("gid")
                .email("u@example.com")
                .name("User")
                .role(Role.PREMIUM)
                .build();
        when(userRepository.findByGoogleId("gid")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.incrementTokenVersion(7L)).thenReturn(1);
        when(userRepository.findTokenVersionById(7L)).thenReturn(Optional.of(1L));

        OAuth2AccessToken googleToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "google-access",
                Instant.now(),
                Instant.now().plusSeconds(3600));
        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
                mock(org.springframework.security.oauth2.client.registration.ClientRegistration.class),
                "u",
                googleToken,
                null);
        when(authorizedClientService.loadAuthorizedClient(any(), any())).thenReturn(client);

        RefreshTokenService.IssuedToken issued = new RefreshTokenService.IssuedToken(
                "raw-refresh-value",
                com.athenyx.backend.entity.RefreshToken.builder()
                        .id(99L)
                        .user(existing)
                        .familyId("fam-1")
                        .tokenHash(new byte[32])
                        .issuedAt(java.time.LocalDateTime.now())
                        .expiresAt(java.time.LocalDateTime.now().plusDays(30))
                        .absoluteExpiresAt(java.time.LocalDateTime.now().plusDays(90))
                        .build(),
                1L);
        when(refreshTokenService.issue(any(User.class), any(HttpServletRequest.class), anyLong()))
                .thenReturn(issued);

        OAuth2User oauthUser = new DefaultOAuth2User(
                List.of(),
                Map.of("sub", "gid", "email", "u@example.com", "name", "User", "picture", "p"),
                "sub");
        Authentication authentication = mock(OAuth2AuthenticationToken.class);
        when(((OAuth2AuthenticationToken) authentication).getPrincipal()).thenReturn(oauthUser);
        when(((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId()).thenReturn("google");
        when(((OAuth2AuthenticationToken) authentication).getName()).thenReturn("u");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        req.setRequestURI("/login/oauth2/code/google");
        MockHttpServletResponse res = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(req, res, authentication);

        Collection<String> setCookies = res.getHeaders("Set-Cookie");
        assertThat(setCookies).hasSize(2);

        List<String> cookies = setCookies.stream().toList();
        assertThat(cookies).anySatisfy(c -> {
            assertThat(c).contains("athenyx_token=");
            assertThat(c).contains("Path=/");
            assertThat(c).contains("HttpOnly");
        });
        assertThat(cookies).anySatisfy(c -> {
            assertThat(c).contains("athenyx_refresh=raw-refresh-value");
            assertThat(c).contains("Path=/");
            assertThat(c).contains("HttpOnly");
        });

        assertThat(res.getRedirectedUrl()).isEqualTo("http://localhost:4200/home");
    }
}
