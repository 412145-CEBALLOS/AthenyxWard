package com.athenyx.backend.security;

import com.athenyx.backend.config.ConfigKey;
import com.athenyx.backend.config.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeatureToggleService {

    private final ConfigService configService;

    public boolean isEnabled(ConfigKey featureKey) {
        return configService.getBoolean(featureKey);
    }
}
