package com.athenyx.backend.config;

import com.athenyx.backend.security.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link CorrelationIdFilter} as a native servlet filter with the
 * highest precedence, so it runs <strong>before</strong> Spring Security's
 * {@code DelegatingFilterProxy} (and therefore before any other filter in
 * the Spring Security chain).
 *
 * <p>This is required for paths handled entirely inside the Spring Security
 * chain (such as the OAuth2 callback {@code /login/oauth2/code/google}) where
 * the {@code OAuth2LoginAuthenticationFilter} invokes
 * {@code OAuth2LoginSuccessHandler} <em>before</em> any
 * {@code addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)} call
 * takes effect — because {@code UsernamePasswordAuthenticationFilter} is not
 * present in the chain when {@code formLogin()} is not configured.</p>
 *
 * <p>See {@code AGENTS.md} for the full diagnosis.</p>
 */
@Configuration
public class CorrelationIdFilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(
            CorrelationIdFilter filter) {
        FilterRegistrationBean<CorrelationIdFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        reg.setName("correlationIdFilter");
        return reg;
    }
}
