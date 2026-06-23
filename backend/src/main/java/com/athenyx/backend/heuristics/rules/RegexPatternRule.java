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

    /**
     * DNI / NIF / NIE. Original pattern {@code \b\d{1,8}[A-Z]\b} matched
     * any invoice line with a small number + letter (e.g. order numbers
     * in receipts). Tightened to require at least 7 digits and to avoid
     * matches surrounded by digits on both sides.
     */
    private static final Pattern DNI_NIF = Pattern.compile(
        "(?<![0-9])\\d{7,8}[A-Z](?![0-9A-Z])",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Passport. Original pattern matched order codes like "AB1234567".
     * Tightened to require at least 7 trailing digits.
     */
    private static final Pattern PASSPORT = Pattern.compile(
        "\\b[A-Z]{1,2}[0-9]{7,9}\\b",
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
        return RuleSeverity.LOW;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String content = input.content() != null ? input.content() : "";

        boolean hasCC = isLuhnValid(CREDIT_CARD.matcher(content));
        boolean hasIBAN = IBAN.matcher(content).find();
        boolean hasDNI = DNI_NIF.matcher(content).find();
        boolean hasPassport = PASSPORT.matcher(content).find();
        boolean hasSSN = SSN.matcher(content).find();

        boolean[] flags = { hasCC, hasIBAN, hasDNI, hasPassport, hasSSN };
        int count = 0;
        for (boolean f : flags) if (f) count++;

        if (count == 0) {
            return Optional.empty();
        }

        // A single sensitive data match is a weak signal — order numbers
        // and tax IDs appear in many legitimate emails. Two or more
        // (e.g. CC + IBAN) is a much stronger indicator of a phishing
        // payload and we keep the original HIGH severity for that.
        int score;
        if (count >= 2) {
            score = 85;
        } else {
            score = 25;
        }

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
