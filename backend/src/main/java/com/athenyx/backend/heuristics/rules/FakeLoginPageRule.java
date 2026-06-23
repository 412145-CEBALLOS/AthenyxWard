package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class FakeLoginPageRule implements HeuristicRule {

    private static final Pattern LOGIN_PATH_PATTERN = Pattern.compile(
        "(/login|/signin|/sign-in|/auth|/account|/verify|/verification|/validar|/acceso|/entrar)" +
        "(\\?.*)?$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FAKE_LOGIN_PARAMS = Pattern.compile(
        "(\\?|&)(redirect|redir|dest|continue|next|return_url|back_to|language|lang|culture|country)=",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public String name() {
        return "FakeLoginPageRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        boolean hasSuspiciousUrl = false;
        boolean hasDeepLogin = false;
        boolean hasFakeHtmlForm = false;

        for (String url : input.urls()) {
            if (url == null) continue;
            if (LOGIN_PATH_PATTERN.matcher(url).find()) {
                hasDeepLogin = true;
            }
            if (FAKE_LOGIN_PARAMS.matcher(url).find()) {
                hasDeepLogin = true;
            }
        }

        String html = input.htmlContent() != null ? input.htmlContent().toLowerCase() : "";
        if (html.contains("<form") && (html.contains("action=\"http://") || html.contains("action='http://"))) {
            if (html.contains("password") || html.contains("contraseña") || html.contains("pin")) {
                hasFakeHtmlForm = true;
            }
        }

        if (!hasSuspiciousUrl && !hasDeepLogin && !hasFakeHtmlForm) {
            return Optional.empty();
        }

        int score = 50;
        if (hasFakeHtmlForm) score = 80;
        else if (hasDeepLogin) score = 50;

        String description = hasFakeHtmlForm
            ? "Formulario HTML con campo de contraseña apuntando a URL no segura"
            : "URL tipo página de login o verificación detectada: posible phishing";

        return Optional.of(new HeuristicFinding(name(), description, score));
    }
}
