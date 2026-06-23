package com.athenyx.backend.heuristics.whitelist;

import java.util.Set;

/**
 * Whitelist of envelope-sender (Return-Path) domains belonging to
 * legitimate Email Service Providers (ESPs) and bulk-mail platforms.
 *
 * <p>When a corporate brand like Nintendo or Google sends email through
 * Amazon SES, Gmail's bounce infrastructure or a marketing platform,
 * the visible {@code From} address belongs to the brand but the
 * hidden {@code Return-Path} domain belongs to the ESP. The
 * {@code ReturnPathMismatchRule} must not flag this as a spoof — a
 * perfect legitimate flow.</p>
 *
 * <h2>Exact-match only</h2>
 *
 * <p>Unlike {@link TrustedSenderDomains} (which uses suffix matching),
 * this whitelist uses <strong>exact-match only</strong> so that
 * homoglyph domains like {@code "sendgr1d.net"} or
 * {@code "amazon-ses.com"} do not pass.</p>
 */
public final class BulkSenderReturnPathDomains {

    // ---- Amazon SES ----------------------------------------------------------
    private static final Set<String> AMAZON_SES = Set.of(
        "amazonses.com", "amazonaws.com", "amazon.com", "amazonsimplemail.com"
    );

    // ---- Google (Gmail / Workspace bounce) -----------------------------------
    private static final Set<String> GOOGLE = Set.of(
        "scoutcamp.bounces.google.com", "bounces.google.com",
        "googlemail.com", "google.com", "gmail.com"
    );

    // ---- SendGrid ------------------------------------------------------------
    private static final Set<String> SENDGRID = Set.of(
        "sendgrid.net", "bounce.sendgrid.net", "bounces.sendgrid.net",
        "em-sj.email.sendgrid.net"
    );

    // ---- Mailgun -------------------------------------------------------------
    private static final Set<String> MAILGUN = Set.of(
        "mailgun.org", "mg.mailgun.org", "mta.mailgun.org"
    );

    // ---- Postmark ------------------------------------------------------------
    private static final Set<String> POSTMARK = Set.of(
        "postmarkapp.com", "pm.mtasv.net"
    );

    // ---- Mailchimp -----------------------------------------------------------
    private static final Set<String> MAILCHIMP = Set.of(
        "mailchimp.com", "mcsv.net", "list-manage.com"
    );

    // ---- Mandrill / Transactional --------------------------------------------
    private static final Set<String> MANDRILL = Set.of(
        "mandrillapp.com", "messagingengine.com"
    );

    // ---- Mailjet -------------------------------------------------------------
    private static final Set<String> MAILJET = Set.of(
        "mailjet.com"
    );

    // ---- Sendinblue / Brevo --------------------------------------------------
    private static final Set<String> BREVO = Set.of(
        "sendinblue.com", "brevo.com", "sib-messaging.com"
    );

    // ---- SparkPost -----------------------------------------------------------
    private static final Set<String> SPARKPOST = Set.of(
        "sparkpost.com", "sparkpostmail.com"
    );

    // ---- HubSpot -------------------------------------------------------------
    private static final Set<String> HUBSPOT = Set.of(
        "hubspot.com", "hs-email.net", "hubspotemail.net", "hsmsgs.com"
    );

    // ---- Constant Contact ----------------------------------------------------
    private static final Set<String> CONSTANT_CONTACT = Set.of(
        "constantcontact.com", "ctsends.com", "ccsend.com"
    );

    // ---- ActiveCampaign ------------------------------------------------------
    private static final Set<String> ACTIVECAMPAIGN = Set.of(
        "activecampaign.com", "activehosted.com"
    );

    // ---- Klaviyo -------------------------------------------------------------
    private static final Set<String> KLAVIYO = Set.of(
        "klaviyo.com", "klaviyo-email.com"
    );

    // ---- Salesforce Marketing Cloud -----------------------------------------
    private static final Set<String> SALESFORCE_MC = Set.of(
        "exacttarget.com", "marketingcloud.com", "mta1.salesforce.com",
        "bounce.s7.exacttarget.com"
    );

    // ---- Oracle Responsys ---------------------------------------------------
    private static final Set<String> RESPONSYS = Set.of(
        "responsys.com", "rsgsv.net", "rsys5.net", "rsys6.net"
    );

    // ---- Zoho ----------------------------------------------------------------
    private static final Set<String> ZOHO = Set.of(
        "zoho.com", "zohoemail.com", "zcsend.com"
    );

    // ---- Atlassian / Statuspage ---------------------------------------------
    private static final Set<String> ATLASSIAN = Set.of(
        "atlassian.net", "statuspage.io", "email.statuspage.io",
        "mail.statuspage.io"
    );

    // ---- DocuSign ------------------------------------------------------------
    private static final Set<String> DOCUSIGN = Set.of(
        "docusign.net", "docusign.com"
    );

    // ---- Facebook / Meta -----------------------------------------------------
    private static final Set<String> FACEBOOK = Set.of(
        "facebookmail.com", "tfbnw.net", "business.facebook.com",
        "facebook.com"
    );

    // ---- LinkedIn ------------------------------------------------------------
    private static final Set<String> LINKEDIN = Set.of(
        "linkedin.com", "em.linkedin.com", "e.linkedin.com"
    );

    // ---- Twitter / X ---------------------------------------------------------
    private static final Set<String> TWITTER = Set.of(
        "twitter.com", "bounce.twitter.com", "bounceremail.twitter.com",
        "x.com"
    );

    // ---- Apple ---------------------------------------------------------------
    private static final Set<String> APPLE = Set.of(
        "apple.com", "email.apple.com"
    );

    // ---- Microsoft -----------------------------------------------------------
    private static final Set<String> MICROSOFT = Set.of(
        "msecnd.net", "notify.windows.com", "accountprotection.microsoft.com",
        "outlook.com", "hotmail.com", "live.com"
    );

    // ---- Adobe ---------------------------------------------------------------
    private static final Set<String> ADOBE = Set.of(
        "adobe.com", "mzstatic.com"
    );

    // ---- Shopify -------------------------------------------------------------
    private static final Set<String> SHOPIFY = Set.of(
        "shopify.com", "shops.email.shopify.com", "shopifyemail.com"
    );

    // ---- Reddit --------------------------------------------------------------
    private static final Set<String> REDDIT = Set.of(
        "reddit.com", "redditmail.com"
    );

    // ---- Twitch --------------------------------------------------------------
    private static final Set<String> TWITCH = Set.of(
        "twitch.tv", "mail.twitch.tv"
    );

    // ---- Stripe --------------------------------------------------------------
    private static final Set<String> STRIPE = Set.of(
        "stripe.com", "email.stripe.com"
    );

    // ---- Zendesk -------------------------------------------------------------
    private static final Set<String> ZENDESK = Set.of(
        "zendesk.com", "em.zendesk.com", "mail.zendesk.com"
    );

    // ---- Intercom ------------------------------------------------------------
    private static final Set<String> INTERCOM = Set.of(
        "intercom-mail.com", "intercom.io", "mail.intercom.io"
    );

    // ---- Freshdesk / Help Scout ---------------------------------------------
    private static final Set<String> SUPPORT_PLATFORMS = Set.of(
        "freshdesk.com", "freshemail.io",
        "helpscout.com", "helpscoutdocs.com", "mail.helpscout.com"
    );

    // ---- Yahoo / Verizon -----------------------------------------------------
    private static final Set<String> YAHOO = Set.of(
        "yahoo.com", "yahoodns.net", "rocketmail.com"
    );

    // ---- Notifications (direct from product) --------------------------------
    private static final Set<String> NOTIFICATIONS = Set.of(
        "notifications.instagram.com", "mail.notifications.google.com",
        "postoffice.x.com", "messaging.squareup.com"
    );

    // ---- Combined set --------------------------------------------------------
    private static final Set<String> ALL = union(
        AMAZON_SES, GOOGLE, SENDGRID, MAILGUN, POSTMARK, MAILCHIMP, MANDRILL,
        MAILJET, BREVO, SPARKPOST, HUBSPOT, CONSTANT_CONTACT, ACTIVECAMPAIGN,
        KLAVIYO, SALESFORCE_MC, RESPONSYS, ZOHO, ATLASSIAN, DOCUSIGN,
        FACEBOOK, LINKEDIN, TWITTER, APPLE, MICROSOFT, ADOBE, SHOPIFY,
        REDDIT, TWITCH, STRIPE, ZENDESK, INTERCOM, SUPPORT_PLATFORMS,
        YAHOO, NOTIFICATIONS
    );

    private BulkSenderReturnPathDomains() {
    }

    /**
     * Exact-match only. Returns {@code true} when {@code domain} is
     * one of the known ESP / bulk-sender return-path domains.
     */
    public static boolean matches(String domain) {
        if (domain == null) return false;
        return ALL.contains(domain.toLowerCase().trim());
    }

    private static <T> Set<T> union(Set<T>... sets) {
        java.util.Set<T> result = new java.util.HashSet<>();
        for (Set<T> s : sets) result.addAll(s);
        return java.util.Collections.unmodifiableSet(result);
    }
}
