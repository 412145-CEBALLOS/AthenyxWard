package com.athenyx.backend.controller;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import com.athenyx.backend.dto.PublicPricingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/public/pricing")
@RequiredArgsConstructor
public class PublicPricingController {

    private final ConfigService configService;

    @GetMapping
    public ResponseEntity<PublicPricingResponse> getPricing() {
        BigDecimal monthly = new BigDecimal(configService.getString(ConfigKey.SUBSCRIPTION_PRICE_MONTHLY));
        BigDecimal annual = new BigDecimal(configService.getString(ConfigKey.SUBSCRIPTION_PRICE_ANNUAL));
        String currency = configService.getString(ConfigKey.SUBSCRIPTION_CURRENCY);
        int savingsPercent = configService.getInt(ConfigKey.SUBSCRIPTION_ANNUAL_SAVINGS_PERCENT);
        String providersCsv = configService.getString(ConfigKey.PAYMENT_ENABLED_PROVIDERS);
        List<String> enabledProviders = Arrays.asList(providersCsv.split(","));

        return ResponseEntity.ok(new PublicPricingResponse(
                monthly,
                annual,
                currency,
                savingsPercent,
                enabledProviders
        ));
    }
}
