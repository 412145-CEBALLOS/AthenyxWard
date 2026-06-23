package com.athenyx.backend.heuristics.whitelist;

import java.util.Set;

/**
 * Whitelist of legitimate corporate sender domains used to suppress
 * false positives in the heuristic engine.
 *
 * <p>Each entry is a <strong>registered root domain</strong>. The
 * matcher in {@link #matches(String)} uses suffix-style matching so a
 * trust on {@code "paypal.com"} also covers subdomains such as
 * {@code "mail.paypal.com"} or {@code "communications.paypal.com"}.</p>
 *
 * <h2>Why suffix matching is safe</h2>
 *
 * <p>Suffix matching with the literal {@code "."} separator rejects
 * every homoglyph / look-alike pattern the engine also defends
 * against:</p>
 *
 * <ul>
 *   <li>{@code "paypal.com"} does <em>not</em> match
 *       {@code "paypal-secure.com"} (no leading dot).</li>
 *   <li>{@code "paypal.com"} does <em>not</em> match
 *       {@code "paypal.com.evil.com"} (does not end with
 *       {@code ".paypal.com"}).</li>
 *   <li>{@code "paypal.com"} does <em>not</em> match
 *       {@code "notpaypal.com"} (no separator before the brand).</li>
 * </ul>
 *
 * <p>Other rules in the engine
 * ({@code SenderImpersonationRule}, {@code SuspiciousDomainRule},
 * {@code ReplyToMismatchRule}, etc.) remain active and would still
 * fire on those look-alikes.</p>
 *
 * <h2>Maintainability</h2>
 *
 * <p>The list is grouped by category into private sets to make
 * additions / removals obvious during code review. Only the combined
 * set is exposed through {@link #matches(String)}.</p>
 *
 * <p>Estimated coverage: 250+ domains representing the most common
 * legitimate transactional, marketing and notification sources a
 * typical user receives (banks, tech giants, retailers, travel,
 * streaming, SaaS, government, education, food delivery, media,
 * crypto, insurance, etc.).</p>
 */
public final class TrustedSenderDomains {

    // ---- 1. Payment / Finance -------------------------------------------------

    private static final Set<String> PAYMENT_PROCESSORS = Set.of(
        "paypal.com", "stripe.com", "wise.com", "revolut.com", "n26.com",
        "monzo.com", "starlingbank.com", "venmo.com", "cashapp.com",
        "squareup.com", "klarna.com", "afterpay.com"
    );

    private static final Set<String> CRYPTO_EXCHANGES = Set.of(
        "coinbase.com", "binance.com", "kraken.com", "crypto.com",
        "gemini.com", "blockchain.com", "bitpay.com", "robinhood.com",
        "etoro.com", "bitfinex.com", "bitstamp.net", "bitgo.com",
        "celsius.net"
    );

    private static final Set<String> BANKS_US = Set.of(
        "chase.com", "bankofamerica.com", "citi.com", "wellsfargo.com",
        "capitalone.com", "discover.com", "americanexpress.com", "amex.com",
        "usaa.com", "pnc.com", "tdbank.com", "regions.com", "5ththird.com",
        "bbt.com", "suntrust.com", "ally.com"
    );

    private static final Set<String> BANKS_EU_UK = Set.of(
        "barclays.co.uk", "lloydsbank.com", "natwest.com", "santander.com",
        "santander.co.uk", "bbva.com", "bbva.es", "caixabank.com",
        "sabadell.com", "bankinter.com", "ing.com", "ing.es",
        "abnamro.com", "rabobank.com", "bnpparibas.com",
        "credit-agricole.fr", "societegenerale.fr", "deutschebank.de",
        "commerzbank.de", "unicredit.it", "intesasanpaolo.com",
        "nordea.com", "danskebank.com", "swedbank.com", "handelsbanken.com"
    );

    private static final Set<String> BANKS_LATAM = Set.of(
        "bancomer.com", "banorte.com", "santander.com.mx", "scotiabank.com",
        "scotiabank.cl", "itau.com.br", "bradesco.com.br",
        "bancodobrasil.com.br", "santander.com.br", "bci.cl",
        "bancoestado.cl", "bancosantander.cl", "mercadopago.com"
    );

    private static final Set<String> BROKERS_INVESTMENTS = Set.of(
        "fidelity.com", "schwab.com", "vanguard.com", "merrilledge.com",
        "etrade.com", "tdameritrade.com", "morganstanley.com",
        "goldmansachs.com", "jpmorgan.com", "wealthfront.com",
        "betterment.com", "acorns.com"
    );

    // ---- 2. Tech giants -------------------------------------------------------

    private static final Set<String> GOOGLE_DOMAINS = Set.of(
        "google.com", "gmail.com", "googlemail.com", "youtube.com",
        "android.com", "nest.com", "chromium.com"
    );

    private static final Set<String> APPLE_DOMAINS = Set.of(
        "apple.com", "icloud.com", "me.com", "mac.com", "itunes.com",
        "applepay.com"
    );

    private static final Set<String> MICROSOFT_DOMAINS = Set.of(
        "microsoft.com", "outlook.com", "hotmail.com", "live.com", "msn.com",
        "office.com", "office365.com", "azure.com", "skype.com", "xbox.com"
    );

    private static final Set<String> AMAZON_DOMAINS = Set.of(
        "amazon.com", "amazon.co.uk", "amazon.de", "amazon.es",
        "amazon.fr", "amazon.it", "amazon.com.mx", "amazon.com.br",
        "amazon.ca", "amazon.com.au", "audible.com", "twitch.tv",
        "ring.com", "imdb.com", "wholefoods.com", "shopbop.com", "zappos.com"
    );

    private static final Set<String> META_DOMAINS = Set.of(
        "meta.com", "facebook.com", "facebookmail.com", "instagram.com",
        "whatsapp.com", "threads.net", "oculus.com"
    );

    private static final Set<String> X_TWITTER = Set.of(
        "twitter.com", "x.com"
    );

    // ---- 3. SaaS & Cloud ------------------------------------------------------

    private static final Set<String> PRODUCTIVITY_SAAS = Set.of(
        "slack.com", "zoom.us", "discord.com", "telegram.org", "signal.org",
        "notion.so", "figma.com", "miro.com", "trello.com", "asana.com",
        "monday.com", "basecamp.com", "linear.app", "airtable.com"
    );

    private static final Set<String> CLOUD_DEVOPS = Set.of(
        "cloudflare.com", "digitalocean.com", "heroku.com", "vercel.com",
        "netlify.com", "fly.io", "render.com", "railway.app", "supabase.com",
        "docker.com", "hashicorp.com", "terraform.io"
    );

    private static final Set<String> CODE_DOCS = Set.of(
        "github.com", "gitlab.com", "bitbucket.org", "atlassian.com",
        "jetbrains.com", "stackoverflow.com", "docusign.net", "docusign.com",
        "dropbox.com", "box.com", "1password.com", "lastpass.com",
        "bitwarden.com"
    );

    // ---- 4. Streaming & Entertainment -----------------------------------------

    private static final Set<String> STREAMING_VIDEO = Set.of(
        "netflix.com", "hulu.com", "disneyplus.com", "hbomax.com", "max.com",
        "paramountplus.com", "peacocktv.com", "appletv.com"
    );

    private static final Set<String> STREAMING_AUDIO = Set.of(
        "spotify.com", "soundcloud.com", "tidal.com", "deezer.com",
        "pandora.com", "iheart.com", "audible.com"
    );

    private static final Set<String> GAMING = Set.of(
        "playstation.com", "sony.com", "nintendo.com", "ea.com",
        "ubisoft.com", "activisionblizzard.com", "riotgames.com",
        "epicgames.com", "roblox.com", "steam.com", "steampowered.com",
        "supercell.com", "zynga.com", "king.com"
    );

    // ---- 5. Retail / E-commerce ----------------------------------------------

    private static final Set<String> MARKETPLACES = Set.of(
        "ebay.com", "etsy.com", "aliexpress.com", "alibaba.com",
        "temu.com", "shein.com", "mercadolibre.com", "mercadolivre.com.br"
    );

    private static final Set<String> BIG_BOX = Set.of(
        "walmart.com", "target.com", "bestbuy.com", "homedepot.com",
        "lowes.com", "costco.com", "kohls.com", "jcpenney.com", "macys.com",
        "nordstrom.com"
    );

    private static final Set<String> HOME_FURNITURE = Set.of(
        "ikea.com", "wayfair.com", "crateandbarrel.com", "potterybarn.com",
        "williams-sonoma.com", "westelm.com", "acehardware.com"
    );

    private static final Set<String> APPAREL = Set.of(
        "nike.com", "adidas.com", "newbalance.com", "underarmour.com",
        "lululemon.com", "rei.com", "patagonia.com", "carhartt.com",
        "levi.com", "gap.com", "oldnavy.com", "hm.com", "zara.com",
        "uniqlo.com", "asos.com", "forever21.com", "abercrombie.com",
        "hollisterco.com"
    );

    private static final Set<String> BEAUTY_PETS = Set.of(
        "sephora.com", "ulta.com", "glossier.com", "fenty.com",
        "chewy.com", "petco.com", "petsmart.com"
    );

    // ---- 6. Travel -----------------------------------------------------------

    private static final Set<String> TRAVEL_OTA = Set.of(
        "airbnb.com", "vrbo.com", "booking.com", "expedia.com",
        "hotels.com", "kayak.com", "priceline.com", "agoda.com",
        "trivago.com", "tripadvisor.com", "skyscanner.com"
    );

    private static final Set<String> AIRLINES_US = Set.of(
        "united.com", "delta.com", "aa.com", "southwest.com", "jetblue.com",
        "alaskaair.com", "spirit.com", "frontier.com",
        "hawaiianairlines.com", "allegiantair.com"
    );

    private static final Set<String> AIRLINES_EU = Set.of(
        "lufthansa.com", "britishairways.com", "airfrance.com", "klm.com",
        "iberia.com", "aerlingus.com", "ryanair.com", "easyjet.com",
        "vueling.com", "norwegian.com", "sas.se", "finnair.com", "tap.pt"
    );

    private static final Set<String> AIRLINES_ASIA = Set.of(
        "qantas.com", "aircanada.com", "westjet.com", "cathaypacific.com",
        "singaporeair.com", "emirates.com", "etihad.com", "qatarairways.com",
        "turkishairlines.com", "jal.co.jp", "ana.co.jp", "koreanair.com",
        "asiana.co.kr", "airindia.com", "indigo.com", "bangkokair.com"
    );

    private static final Set<String> HOTEL_CHAINS = Set.of(
        "marriott.com", "hilton.com", "hyatt.com", "ihg.com", "accor.com",
        "bestwestern.com", "wyndham.com", "choicehotels.com",
        "radissonhotels.com"
    );

    private static final Set<String> CRUISES = Set.of(
        "royalcaribbean.com", "carnival.com", "ncl.com", "princess.com",
        "celebrity.com", "disneycruise.com"
    );

    // ---- 7. Delivery / Logistics / Food --------------------------------------

    private static final Set<String> COURIERS = Set.of(
        "ups.com", "usps.com", "fedex.com", "dhl.com", "dpd.com",
        "royalmail.com", "parcelforce.com", "correos.es", "correos.cl",
        "canadapost.ca", "auspost.com.au", "japanpost.jp", "correios.com.br"
    );

    private static final Set<String> FOOD_DELIVERY = Set.of(
        "doordash.com", "ubereats.com", "grubhub.com", "postmates.com",
        "seamless.com", "deliveroo.com", "justeat.com", "takeaway.com",
        "glovo.com", "rappi.com", "ifood.com.br"
    );

    private static final Set<String> RIDE_HAILING = Set.of(
        "uber.com", "lyft.com", "bolt.eu", "grab.com", "didiglobal.com",
        "ola.com", "99app.com"
    );

    private static final Set<String> GROCERY_DELIVERY = Set.of(
        "instacart.com", "shipt.com", "freshdirect.com", "peapod.com"
    );

    // ---- 8. Media / News ------------------------------------------------------

    private static final Set<String> NEWS = Set.of(
        "nytimes.com", "washingtonpost.com", "wsj.com", "bloomberg.com",
        "ft.com", "economist.com", "reuters.com", "ap.org", "bbc.com",
        "cnn.com", "foxnews.com", "npr.org", "theguardian.com",
        "latimes.com", "chicagotribune.com"
    );

    private static final Set<String> BUSINESS_MEDIA = Set.of(
        "forbes.com", "fortune.com", "businessinsider.com",
        "fastcompany.com", "inc.com", "hbr.org"
    );

    private static final Set<String> TECH_MEDIA = Set.of(
        "techcrunch.com", "theverge.com", "arstechnica.com", "engadget.com",
        "wired.com", "cnet.com", "zdnet.com", "mashable.com"
    );

    private static final Set<String> CREATOR_PLATFORMS = Set.of(
        "medium.com", "substack.com", "patreon.com", "kickstarter.com",
        "gofundme.com", "indiegogo.com"
    );

    // ---- 9. Health / Insurance / Pharma ---------------------------------------

    private static final Set<String> PHARMACY = Set.of(
        "cvs.com", "walgreens.com", "riteaid.com"
    );

    private static final Set<String> HEALTH_INSURANCE = Set.of(
        "humana.com", "aetna.com", "cigna.com", "anthem.com",
        "kaiserpermanente.org", "unitedhealthcare.com", "uhc.com",
        "bcbs.com", "oscarhealth.com"
    );

    private static final Set<String> GENERAL_INSURANCE = Set.of(
        "geico.com", "progressive.com", "statefarm.com", "allstate.com",
        "libertymutual.com", "nationwide.com", "travelers.com",
        "metlife.com", "prudential.com", "allianz.com", "axa.com"
    );

    private static final Set<String> PHARMA = Set.of(
        "pfizer.com", "jnj.com", "roche.com", "novartis.com", "merck.com",
        "gsk.com", "sanofi.com", "astrazeneca.com", "lilly.com", "abbvie.com"
    );

    // ---- 10. Government / Education -------------------------------------------

    private static final Set<String> GOV_US = Set.of(
        "irs.gov", "ssa.gov", "uscis.gov", "state.gov", "ftb.ca.gov",
        "cdtfa.ca.gov", "dmv.ca.gov", "ny.gov", "ca.gov", "usa.gov",
        "healthcare.gov", "medicare.gov", "medicaid.gov"
    );

    private static final Set<String> GOV_EU = Set.of(
        "gov.uk", "hmrc.gov.uk", "dvla.gov.uk", "europa.eu"
    );

    private static final Set<String> GOV_LATAM = Set.of(
        "gob.es", "agenciatributaria.es", "seg-social.es",
        "sat.gob.mx", "imss.gob.mx", "gob.mx", "gob.ar", "sunat.gob.pe"
    );

    private static final Set<String> EDUCATION = Set.of(
        "harvard.edu", "mit.edu", "stanford.edu", "berkeley.edu",
        "ox.ac.uk", "cam.ac.uk", "coursera.org", "edx.org", "udemy.com",
        "khanacademy.org", "duolingo.com", "babbel.com", "rosettastone.com"
    );

    // ---- 11. Food / Restaurants -----------------------------------------------

    private static final Set<String> FAST_FOOD = Set.of(
        "starbucks.com", "dunkindonuts.com", "chipotle.com", "mcdonalds.com",
        "kfc.com", "pizzahut.com", "dominos.com", "tacobell.com",
        "panerabread.com", "subway.com", "wendys.com", "burgerking.com",
        "popeyes.com", "chick-fil-a.com"
    );

    private static final Set<String> CASUAL_DINING = Set.of(
        "olivegarden.com", "applebees.com", "chilis.com", "outback.com",
        "cheesecakefactory.com", "texasroadhouse.com"
    );

    // ---- 12. Social & Misc ----------------------------------------------------

    private static final Set<String> SOCIAL_MISC = Set.of(
        "reddit.com", "pinterest.com", "snap.com", "snapchat.com",
        "tiktok.com", "yelp.com", "glassdoor.com", "indeed.com",
        "monster.com", "ziprecruiter.com", "opentable.com", "resy.com",
        "vimeo.com", "flickr.com", "weather.com", "accuweather.com",
        "claude.com", "anthropic.com"
    );

    // ---- Combined set (all of the above) --------------------------------------

    private static final Set<String> ALL = union(
        PAYMENT_PROCESSORS, CRYPTO_EXCHANGES, BANKS_US, BANKS_EU_UK,
        BANKS_LATAM, BROKERS_INVESTMENTS, GOOGLE_DOMAINS, APPLE_DOMAINS,
        MICROSOFT_DOMAINS, AMAZON_DOMAINS, META_DOMAINS, X_TWITTER,
        PRODUCTIVITY_SAAS, CLOUD_DEVOPS, CODE_DOCS, STREAMING_VIDEO,
        STREAMING_AUDIO, GAMING, MARKETPLACES, BIG_BOX, HOME_FURNITURE,
        APPAREL, BEAUTY_PETS, TRAVEL_OTA, AIRLINES_US, AIRLINES_EU,
        AIRLINES_ASIA, HOTEL_CHAINS, CRUISES, COURIERS, FOOD_DELIVERY,
        RIDE_HAILING, GROCERY_DELIVERY, NEWS, BUSINESS_MEDIA, TECH_MEDIA,
        CREATOR_PLATFORMS, PHARMACY, HEALTH_INSURANCE, GENERAL_INSURANCE,
        PHARMA, GOV_US, GOV_EU, GOV_LATAM, EDUCATION, FAST_FOOD,
        CASUAL_DINING, SOCIAL_MISC
    );

    private TrustedSenderDomains() {
    }

    /**
     * Suffix-style match: {@code true} if {@code domain} equals a
     * trusted root, or is a subdomain of one (e.g.
     * {@code "mail.paypal.com"} is trusted when {@code "paypal.com"} is
     * in the set).
     */
    public static boolean matches(String domain) {
        if (domain == null) return false;
        String d = domain.toLowerCase().trim();
        if (d.isEmpty()) return false;
        for (String trusted : ALL) {
            if (d.equals(trusted) || d.endsWith("." + trusted)) {
                return true;
            }
        }
        return false;
    }

    private static <T> Set<T> union(Set<T>... sets) {
        java.util.Set<T> result = new java.util.HashSet<>();
        for (Set<T> s : sets) result.addAll(s);
        return java.util.Collections.unmodifiableSet(result);
    }
}
