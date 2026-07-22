package com.athenyx.backend.payment;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.config.ConfigNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoProperties {

    private final ConfigService configService;

    private boolean enabled = true;
    private String accessToken = "";
    private String publicKey = "";
    private boolean sandbox = true;
    private String backUrlSuccess = "http://localhost:4200/checkout/return";
    private String backUrlSuccessBackend = "https://hatchling-pebble-outpost.ngrok-free.dev/api/checkout/return";
    private String backUrlFailure = "http://localhost:4200/checkout/return";
    private String backUrlPending = "http://localhost:4200/checkout/return";
    private String notificationUrl = "";
    private String ipAllowlist = "209.225.49.0/24,216.33.197.0/24,64.7.219.0/24";

    @PostConstruct
    public void init() {
        try {
            this.enabled = configService.getBoolean(ConfigKey.MERCADOPAGO_ENABLED);
            this.accessToken = configService.getString(ConfigKey.MERCADOPAGO_ACCESS_TOKEN);
            this.publicKey = configService.getString(ConfigKey.MERCADOPAGO_PUBLIC_KEY);
            this.sandbox = configService.getBoolean(ConfigKey.MERCADOPAGO_SANDBOX);
            this.backUrlSuccess = configService.getString(ConfigKey.MERCADOPAGO_BACK_URL_SUCCESS);
            this.backUrlSuccessBackend = configService.getString(ConfigKey.MERCADOPAGO_BACK_URL_SUCCESS_BACKEND);
            this.backUrlFailure = configService.getString(ConfigKey.MERCADOPAGO_BACK_URL_FAILURE);
            this.backUrlPending = configService.getString(ConfigKey.MERCADOPAGO_BACK_URL_PENDING);
            this.notificationUrl = configService.getString(ConfigKey.MERCADOPAGO_NOTIFICATION_URL);
            this.ipAllowlist = configService.getString(ConfigKey.MERCADOPAGO_IP_ALLOWLIST);
            log.info("[MercadoPagoProperties] Loaded config: enabled={}, sandbox={}, hasToken={}",
                    enabled, sandbox, !accessToken.isBlank());
        } catch (ConfigNotFoundException e) {
            log.warn("[MercadoPagoProperties] Config keys not yet seeded, using defaults: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[MercadoPagoProperties] Failed to load from DB config, using defaults: {}", e.getMessage());
        }
    }

    public boolean isConfigured() {
        return enabled && accessToken != null && !accessToken.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public String getBackUrlSuccess() {
        return backUrlSuccess;
    }

    public String getBackUrlSuccessBackend() {
        return backUrlSuccessBackend;
    }

    public String getBackUrlFailure() {
        return backUrlFailure;
    }

    public String getBackUrlPending() {
        return backUrlPending;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public String getIpAllowlist() {
        return ipAllowlist;
    }
}
