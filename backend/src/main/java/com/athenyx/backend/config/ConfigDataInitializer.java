package com.athenyx.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigDataInitializer implements ApplicationRunner {

    private final AppConfigRepository repository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            cleanupEmptyIntValues();
            syncPubliclyVisible();
            insertMissingKeys();
            return;
        }
        log.info("Seeding app_config with {} entries", ConfigKey.values().length);
        Arrays.stream(ConfigKey.values()).forEach(key -> {
            AppConfig entity = AppConfig.builder()
                .configKey(key.name())
                .value(key.getMetadata().defaultValue())
                .type(key.getMetadata().type().name())
                .description(key.getMetadata().description())
                .category(key.getMetadata().category().name())
                .minValue(key.getMetadata().minValue())
                .maxValue(key.getMetadata().maxValue())
                .publiclyVisible(key.getMetadata().publiclyVisible())
                .build();
            repository.save(entity);
        });
        log.info("app_config seed complete");
    }

    private void syncPubliclyVisible() {
        int synced = 0;
        for (ConfigKey key : ConfigKey.values()) {
            AppConfig cfg = repository.findByConfigKey(key.name()).orElse(null);
            if (cfg == null) continue;
            boolean meta = key.getMetadata().publiclyVisible();
            if (cfg.isPubliclyVisible() != meta) {
                cfg.setPubliclyVisible(meta);
                repository.save(cfg);
                synced++;
            }
        }
        if (synced > 0) {
            log.info("app_config sync: updated publicly_visible on {} rows", synced);
        }
    }

    private void cleanupEmptyIntValues() {
        int fixed = 0;
        for (ConfigKey key : ConfigKey.values()) {
            if (key.getMetadata().type() != ConfigType.INT) continue;
            AppConfig cfg = repository.findByConfigKey(key.name()).orElse(null);
            if (cfg == null) continue;
            String v = cfg.getValue();
            if (v == null || v.isBlank()) {
                cfg.setValue(key.getMetadata().defaultValue());
                repository.save(cfg);
                log.info("Repaired empty value for {}: set to default '{}'", key.name(), key.getMetadata().defaultValue());
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("app_config cleanup: repaired {} empty INT values", fixed);
        }
    }

    private void insertMissingKeys() {
        int inserted = 0;
        for (ConfigKey key : ConfigKey.values()) {
            if (repository.findByConfigKey(key.name()).isPresent()) {
                continue;
            }
            AppConfig entity = AppConfig.builder()
                .configKey(key.name())
                .value(key.getMetadata().defaultValue())
                .type(key.getMetadata().type().name())
                .description(key.getMetadata().description())
                .category(key.getMetadata().category().name())
                .minValue(key.getMetadata().minValue())
                .maxValue(key.getMetadata().maxValue())
                .publiclyVisible(key.getMetadata().publiclyVisible())
                .build();
            repository.save(entity);
            inserted++;
            log.info("Inserted missing app_config key: {}", key.name());
        }
        if (inserted > 0) {
            log.info("app_config: inserted {} missing keys", inserted);
        }
    }
}
