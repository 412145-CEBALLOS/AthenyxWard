package com.athenyx.backend.config;

import com.athenyx.backend.security.JwtAuthenticationFilter;
import com.athenyx.backend.security.OAuth2LoginFailureHandler;
import com.athenyx.backend.security.OAuth2LoginSuccessHandler;
import com.athenyx.backend.security.RateLimitFilter;
import com.athenyx.backend.security.RefreshOriginFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central Spring Security configuration.
 *
 * <p>Configures the {@link SecurityFilterChain} for the stateless REST API:
 * <ul>
 *     <li>CORS allow-list pinned to {@code app.frontend.url}.</li>
 *     <li>CSRF disabled — the API relies on same-origin cookies plus
 *         {@link com.athenyx.backend.security.RefreshOriginFilter}.</li>
 *     <li>Optional HTTPS-redirect + HSTS, gated on {@code server.force-https}.</li>
 *     <li>Google OAuth2 login with custom success handler.</li>
 *     <li>401 entry point for {@code /api/**} requests.</li>
 *     <li>Filter ordering: {@code RefreshOriginFilter} →
 *         {@code JwtAuthenticationFilter} → …</li>
 * </ul>
 *
 * <p>Also exposes a custom {@link OAuth2AuthorizationRequestResolver} that
 * forces {@code access_type=offline} and {@code prompt=consent} so Google
 * always returns a refresh token we can persist.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final RefreshOriginFilter refreshOriginFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${server.force-https:false}")
    private boolean forceHttps;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (forceHttps) {
            http.addFilterBefore(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                                                HttpServletResponse response,
                                                FilterChain filterChain)
                        throws ServletException, IOException {
                    if (request.getHeader("X-Forwarded-Proto") != null &&
                        !request.getHeader("X-Forwarded-Proto").equals("https")) {
                        response.sendRedirect("https://" + request.getServerName() + request.getRequestURI());
                        return;
                    }
                    filterChain.doFilter(request, response);
                }
            }, UsernamePasswordAuthenticationFilter.class);

            http.headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)));
        }

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/login/oauth2/**").permitAll()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/")))
                .addFilterBefore(refreshOriginFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver() {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");
        return new CustomAuthorizationRequestResolver(defaultResolver);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
        private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;

        CustomAuthorizationRequestResolver(DefaultOAuth2AuthorizationRequestResolver defaultResolver) {
            this.defaultResolver = defaultResolver;
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
            return customize(request, null);
        }

        @Override
        public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
            return customize(request, clientRegistrationId);
        }

        private OAuth2AuthorizationRequest customize(HttpServletRequest request, String clientRegistrationId) {
            OAuth2AuthorizationRequest req = clientRegistrationId != null
                    ? defaultResolver.resolve(request, clientRegistrationId)
                    : defaultResolver.resolve(request);
            if (req == null) return null;

            Map<String, Object> additionalParams = new LinkedHashMap<>(req.getAdditionalParameters());
            additionalParams.put("access_type", "offline");
            additionalParams.put("prompt", "consent");

            return OAuth2AuthorizationRequest.from(req)
                    .additionalParameters(additionalParams)
                    .build();
        }
    }
}