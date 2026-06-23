package com.athenyx.backend.heuristics.rules;

import com.athenyx.backend.heuristics.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class DisplayNameBrandSpoofRule implements HeuristicRule {

    private static final Set<String> GENERIC_BRAND_TERMS = Set.of(
        "soporte", "support", "seguridad", "security", "alerta", "alert",
        "notificaciones", "notifications", "noreply", "no-reply", "no.reply",
        "admin", "administrador", "equipo", "team", "servicio", "service",
        "atencion", "atención", "ayuda", "help", "verificacion", "verificación",
        "banco", "bank", "cuenta", "account", "factura", "invoice",
        "pago", "payment", "transferencia", "seguro", "urgent", "emergencia",
        "actualizacion", "actualización", "confirmar", "validar"
    );

    @Override
    public String name() {
        return "DisplayNameBrandSpoofRule";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.MEDIUM;
    }

    @Override
    public Optional<HeuristicFinding> apply(EmailHeuristicsInput input) {
        String displayName = input.senderName();
        if (displayName == null || displayName.isBlank()) {
            return Optional.empty();
        }

        String senderDomain = extractDomain(input.sender());
        if (senderDomain == null) {
            return Optional.empty();
        }

        String displayLower = displayName.toLowerCase();
        String senderDomainLower = senderDomain.toLowerCase();

        for (String term : GENERIC_BRAND_TERMS) {
            if (displayLower.contains(term)) {
                boolean isLikelyLegit = senderDomainLower.contains(term) ||
                    Set.of("google.com", "microsoft.com", "apple.com", "amazon.com",
                           "facebook.com", "paypal.com", "spotify.com", "netflix.com")
                           .contains(senderDomainLower);

                if (!isLikelyLegit) {
                    return Optional.of(new HeuristicFinding(
                        name(),
                        "Display name '" + displayName + "' usa término genérico de marca ('" + term + "') sin ser del dominio '" + senderDomain + "'",
                        85
                    ));
                }
            }
        }

        return Optional.empty();
    }

    private String extractDomain(String sender) {
        int at = sender.indexOf('@');
        if (at < 0) return null;
        int domainEnd = sender.indexOf('>', at);
        String domain = sender.substring(at + 1);
        if (domainEnd > at) {
            domain = sender.substring(at + 1, domainEnd);
        }
        return domain.trim();
    }
}
