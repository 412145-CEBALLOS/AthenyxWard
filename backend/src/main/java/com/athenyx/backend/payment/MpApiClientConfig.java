package com.athenyx.backend.payment;

import com.athenyx.backend.config.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MpApiClientConfig {

    private final MercadoPagoProperties mercadoPagoProperties;
    private final ConfigService configService;

    @Bean
    public MpApiClient mpApiClient() {
        return new MpApiClient(mercadoPagoProperties, configService);
    }
}
