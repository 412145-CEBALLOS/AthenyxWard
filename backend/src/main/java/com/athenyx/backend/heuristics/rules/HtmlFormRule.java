package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class HtmlFormRule implements HeuristicRule {

    private static final Pattern HTML_FORM = Pattern.compile(
        "<form[^>]*>.*?</form>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern HTTP_ACTION = Pattern.compile(
        "action\\s*=\\s*[\"']http://",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SENSITIVE_INPUT = Pattern.compile(
        "(<input[^>]*type\\s*=\\s*[\"']password[\"'][^>]*>|<input[^>]*name\\s*=\\s*[\"']password[\"'][^>]*>|" +
        "<input[^>]*type\\s*=\\s*[\"']cc[\"'][^>]*>|<input[^>]*name\\s*=\\s*[\"']cc[\"'][^>]*>|" +
        "<input[^>]*name\\s*=\\s*[\"']card_number[\"'][^>]*>|<input[^>]*name\\s*=\\s*[\"']cvv[\"'][^>]*>)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public String name() {
        return "HtmlFormRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String html = input.htmlContent() != null ? input.htmlContent() : "";
        if (html.isBlank()) {
            return Optional.empty();
        }

        boolean hasForm = false;
        boolean hasHttpAction = false;
        boolean hasSensitiveInput = false;

        var formMatcher = HTML_FORM.matcher(html);
        while (formMatcher.find()) {
            hasForm = true;
            String formContent = formMatcher.group();
            if (HTTP_ACTION.matcher(formContent).find()) {
                hasHttpAction = true;
            }
            if (SENSITIVE_INPUT.matcher(formContent).find()) {
                hasSensitiveInput = true;
            }
        }

        if (!hasForm) {
            return Optional.empty();
        }

        int score;
        String description;
        if (hasSensitiveInput && hasHttpAction) {
            score = 95;
            description = "Formulario HTML con campos sensibles (contraseña/tarjeta) y action HTTP no seguro";
        } else if (hasSensitiveInput) {
            score = 70;
            description = "Formulario HTML con campos sensibles detectados (contraseña, tarjeta, CVV)";
        } else if (hasHttpAction) {
            score = 70;
            description = "Formulario HTML con action apuntando a URL HTTP no segura";
        } else {
            score = 50;
            description = "Formulario HTML detectado en el cuerpo del correo";
        }

        return Optional.of(new HeuristicFinding(name(), description, score));
    }
}
