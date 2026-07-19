package com.athenyx.backend.config;

import com.athenyx.backend.config.dto.ConfigCategoryResponse;
import com.athenyx.backend.config.dto.ConfigEntryResponse;
import com.athenyx.backend.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final AppConfigRepository repository;

    @Cacheable(value = CacheConfig.APP_CONFIG_CACHE, key = "#key.name()")
    public String getRaw(ConfigKey key) {
        return repository.findByConfigKey(key.name())
            .orElseThrow(() -> new ConfigNotFoundException(key.name()))
            .getValue();
    }

    public int getInt(ConfigKey key) {
        String raw = getRaw(key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            log.warn("Config key {} is not a valid integer: {}", key.name(), raw);
            return Integer.parseInt(key.getMetadata().defaultValue());
        }
    }

    public boolean getBoolean(ConfigKey key) {
        return Boolean.parseBoolean(getRaw(key));
    }

    public String getString(ConfigKey key) {
        return getRaw(key);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.APP_CONFIG_CACHE, key = "'all'")
    public List<ConfigCategoryResponse> getAllGrouped() {
        List<AppConfig> all = repository.findAll();
        Map<ConfigCategory, List<ConfigEntryResponse>> grouped = new EnumMap<>(ConfigCategory.class);

        for (AppConfig cfg : all) {
            ConfigCategory cat = ConfigCategory.valueOf(cfg.getCategory());
            grouped.computeIfAbsent(cat, k -> new java.util.ArrayList<>()).add(toResponse(cfg));
        }

        return java.util.Arrays.stream(ConfigCategory.values())
            .filter(grouped::containsKey)
            .map(cat -> ConfigCategoryResponse.builder()
                .category(cat)
                .categoryLabel(cat.getLabel())
                .entries(grouped.get(cat))
                .build())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ConfigCategoryResponse> getPublicGrouped() {
        return getAllGrouped().stream()
            .map(cat -> ConfigCategoryResponse.builder()
                .category(cat.getCategory())
                .categoryLabel(cat.getCategoryLabel())
                .entries(cat.getEntries().stream()
                    .filter(e -> e.isPubliclyVisible())
                    .toList())
                .build())
            .filter(cat -> !cat.getEntries().isEmpty())
            .toList();
    }

    @Transactional(readOnly = true)
    public ConfigEntryResponse getByKey(String keyName) {
        ConfigKey key = ConfigKey.findByName(keyName)
            .orElseThrow(() -> new ConfigNotFoundException(keyName));
        AppConfig cfg = repository.findByConfigKey(key.name())
            .orElseThrow(() -> new ConfigNotFoundException(keyName));
        return toResponse(cfg);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.APP_CONFIG_CACHE, allEntries = true)
    public ConfigEntryResponse set(ConfigKey key, String rawValue, User actor) {
        validate(key, rawValue);

        AppConfig cfg = repository.findByConfigKey(key.name())
            .orElseThrow(() -> new ConfigNotFoundException(key.name()));

        cfg.setValue(rawValue);
        cfg.setUpdatedBy(actor);

        AppConfig saved = repository.save(cfg);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Config key {} updated to '{}' by {}",
                    key.name(), rawValue, actor != null ? actor.getEmail() : "system");
            }
        });

        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.APP_CONFIG_CACHE, allEntries = true)
    public void evictCache() {
        log.debug("appConfig cache evicted");
    }

    private void validate(ConfigKey key, String rawValue) {
        ConfigMetadata meta = key.getMetadata();

        switch (meta.type()) {
            case INT -> {
                int val;
                try {
                    val = Integer.parseInt(rawValue);
                } catch (NumberFormatException e) {
                    throw new ConfigValidationException("value", rawValue,
                        "El valor debe ser un número entero para la clave " + key.name());
                }
                if (meta.minValue() != null && val < meta.minValue()) {
                    throw new ConfigValidationException("value", rawValue,
                        "El valor debe ser mayor o igual a " + meta.minValue());
                }
                if (meta.maxValue() != null && val > meta.maxValue()) {
                    throw new ConfigValidationException("value", rawValue,
                        "El valor debe ser menor o igual a " + meta.maxValue());
                }
            }
            case BOOLEAN -> {
                if (!rawValue.equalsIgnoreCase("true") && !rawValue.equalsIgnoreCase("false")) {
                    throw new ConfigValidationException("value", rawValue,
                        "El valor debe ser 'true' o 'false' para la clave " + key.name());
                }
            }
            case STRING -> {
                if (rawValue == null || rawValue.isBlank()) {
                    throw new ConfigValidationException("value", rawValue,
                        "El valor no puede estar vacío para la clave " + key.name());
                }
            }
        }
    }

    private ConfigEntryResponse toResponse(AppConfig cfg) {
        String updatedByEmail = cfg.getUpdatedBy() != null ? cfg.getUpdatedBy().getEmail() : null;
        return ConfigEntryResponse.builder()
            .key(cfg.getConfigKey())
            .value(cfg.getValue())
            .type(ConfigType.valueOf(cfg.getType()))
            .description(cfg.getDescription())
            .category(ConfigCategory.valueOf(cfg.getCategory()))
            .minValue(cfg.getMinValue())
            .maxValue(cfg.getMaxValue())
            .publiclyVisible(cfg.isPubliclyVisible())
            .updatedAt(cfg.getUpdatedAt())
            .updatedBy(updatedByEmail)
            .build();
    }
}
