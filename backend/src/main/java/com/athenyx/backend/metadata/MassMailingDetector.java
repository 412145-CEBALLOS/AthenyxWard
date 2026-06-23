package com.athenyx.backend.metadata;

import org.springframework.stereotype.Component;

@Component
public class MassMailingDetector {

    public MassMailingResult detect(ExtractedHeaders headers) {
        MassMailingProvider provider = headers.massMailingProvider();
        if (provider == null || provider == MassMailingProvider.NONE) {
            if (headers.listUnsubscribe() != null && !headers.listUnsubscribe().isBlank()) {
                return new MassMailingResult(true, MassMailingProvider.CUSTOM_CAMPAIGN,
                    "List-Unsubscribe header presente");
            }
            if (headers.xMailer() != null) {
                String xm = headers.xMailer().toLowerCase();
                if (xm.contains("mailchimp") || xm.contains("sendgrid") || xm.contains("mandrill")
                    || xm.contains("postmark") || xm.contains("mailgun") || xm.contains("sendinblue")) {
                    MassMailingProvider detected = MassMailingProvider.detect(
                        headers.xMailer(), headers.listUnsubscribe(), null, null);
                    return new MassMailingResult(true, detected, "X-Mailer indica servicio de envío masivo");
                }
            }
            return new MassMailingResult(false, MassMailingProvider.NONE, null);
        }
        return new MassMailingResult(true, provider, "Servicio de envío masivo detectado: " + provider.name());
    }
}
