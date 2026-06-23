package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class RegexPatternRule implements HeuristicRule {

    private static final Pattern CREDIT_CARD = Pattern.compile(
        "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b"
    );

    private static final Pattern IBAN = Pattern.compile(
        "\\b[A-Z]{2}[0-9]{2}[A-Z0-9]{4}[0-9]{7}(?:[A-Z0-9]?){0,16}\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DNI_NIF = Pattern.compile(
        "\\b\\d{1,8}[A-Z]\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PASSPORT = Pattern.compile(
        "\\b[A-Z]{1,2}[0-9]{6,9}\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SSN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    @Override
    public String name() {
        return "RegexPatternRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.HIGH;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String content = input.content() != null ? input.content() : "";

        boolean hasCC = isLuhnValid(CREDIT_CARD.matcher(content));
        boolean hasIBAN = IBAN.matcher(content).find();
        boolean hasDNI = DNI_NIF.matcher(content).find();
        boolean hasPassport = PASSPORT.matcher(content).find();
        boolean hasSSN = SSN.matcher(content).find();

        int count = (hasCC ? 1 : 0) + (hasIBAN ? 1 : 0) + (hasDNI ? 1 : 0) +
                    (hasPassport ? 1 : 0) + (hasSSN ? 1 : 0);

        if (count == 0) {
            return Optional.empty();
        }

        int score = count >= 2 ? 95 : 70;
        StringBuilder desc = new StringBuilder("Datos sensibles detectados en el cuerpo del correo:");
        if (hasCC) desc.append(" tarjeta de crédito,");
        if (hasIBAN) desc.append(" IBAN,");
        if (hasDNI) desc.append(" documento de identidad,");
        if (hasPassport) desc.append(" pasaporte,");
        if (hasSSN) desc.append(" SSN,");
        String description = desc.substring(0, desc.length() - 1);

        return Optional.of(new HeuristicFinding(name(), description, score));
    }

    private boolean isLuhnValid(java.util.regex.Matcher matcher) {
        String digits = "";
        while (matcher.find()) {
            digits += matcher.group();
        }
        if (digits.isEmpty()) return false;
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(digits.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
