package com.athenyx.backend.config;

import java.util.Arrays;
import java.util.Optional;

public enum ConfigKey {
    AUDIT_RETENTION_DAYS(
        new ConfigMetadata(
            ConfigCategory.RETENTION,
            ConfigType.INT,
            "365",
            "Días que se conservan los registros de auditoría antes de ser purgados automáticamente.",
            1, 3650, false
        )
    ),
    EMAIL_RETENTION_DAYS(
        new ConfigMetadata(
            ConfigCategory.RETENTION,
            ConfigType.INT,
            "180",
            "Días que se conservan los correos analizados. Los correos con recordatorios activos no se ven afectados.",
            1, 1825, false
        )
    ),
    AI_ENABLED(
        new ConfigMetadata(
            ConfigCategory.AI,
            ConfigType.BOOLEAN,
            "true",
            "Interruptor global de las explicaciones con IA. Solo afecta a nuevas requests; las async en curso terminan normalmente.",
            null, null, true
        )
    ),
    AI_MODEL(
        new ConfigMetadata(
            ConfigCategory.AI,
            ConfigType.STRING,
            "qwen2.5:7b",
            "Modelo Ollama a utilizar para las explicaciones con IA.",
            null, null, false
        )
    ),
    TRIAL_ANALYSIS_LIMIT(
        new ConfigMetadata(
            ConfigCategory.QUOTAS,
            ConfigType.INT,
            "20",
            "Cantidad máxima de análisis heurísticos permitidos a un usuario TRIAL.",
            1, 1000, false
        )
    ),
    REMINDER_MAX_PER_USER(
        new ConfigMetadata(
            ConfigCategory.QUOTAS,
            ConfigType.INT,
            "50",
            "Máximo de recordatorios activos por usuario.",
            1, 500, false
        )
    ),
    HEURISTIC_RISK_THRESHOLD_LOW(
        new ConfigMetadata(
            ConfigCategory.HEURISTIC,
            ConfigType.INT,
            "40",
            "Porcentaje por debajo del cual un correo se clasifica como riesgo bajo (verde).",
            0, 100, true
        )
    ),
    HEURISTIC_RISK_THRESHOLD_MEDIUM(
        new ConfigMetadata(
            ConfigCategory.HEURISTIC,
            ConfigType.INT,
            "70",
            "Porcentaje por debajo del cual un correo se clasifica como riesgo medio (amarillo). Por encima o igual: alto (rojo).",
            0, 100, true
        )
    ),
    HEURISTIC_CACHE_HOURS(
        new ConfigMetadata(
            ConfigCategory.HEURISTIC,
            ConfigType.INT,
            "24",
            "Horas que se cachea el resultado de un análisis heurístico antes de permitir re-análisis.",
            1, 168, false
        )
    ),
    NOTIFICATIONS_UPCOMING_WINDOW_HOURS(
        new ConfigMetadata(
            ConfigCategory.NOTIFICATIONS,
            ConfigType.INT,
            "24",
            "Ventana en horas para el endpoint de recordatorios próximos (incluye vencidos).",
            1, 168, false
        )
    ),
    NOTIFICATIONS_POLL_INTERVAL_SECONDS(
        new ConfigMetadata(
            ConfigCategory.NOTIFICATIONS,
            ConfigType.INT,
            "120",
            "Intervalo en segundos del polling del panel de notificaciones en el frontend.",
            30, 3600, true
        )
    ),
    RATELIMIT_EXPLAIN_PER_HOUR(
        new ConfigMetadata(
            ConfigCategory.RATE_LIMIT,
            ConfigType.INT,
            "30",
            "Máximo de explicaciones IA por usuario por hora.",
            1, 10000, false
        )
    ),
    COPY_SUPPORT_EMAIL(
        new ConfigMetadata(
            ConfigCategory.COPY,
            ConfigType.STRING,
            "soporte@athenyxward.com",
            "Dirección de email de soporte visible en el footer y estados vacíos.",
            null, null, true
        )
    ),
    OAUTH_ALLOWED_DOMAINS(
        new ConfigMetadata(
            ConfigCategory.SECURITY,
            ConfigType.STRING,
            "",
            "Lista CSV de dominios de email permitidos para login OAuth2 (ej. 'athenyx.com,empresa.com'). Si está vacía, cualquier dominio es aceptado.",
            null, null, false
        )
    ),
    SECURITY_MAX_FAILED_LOGINS(
        new ConfigMetadata(
            ConfigCategory.SECURITY,
            ConfigType.INT,
            "5",
            "Intentos fallidos de login antes de bloquear la cuenta durante 15 minutos.",
            1, 50, false
        )
    ),
    SECURITY_IP_BLOCKLIST(
        new ConfigMetadata(
            ConfigCategory.SECURITY,
            ConfigType.STRING,
            "",
            "Lista CSV de direcciones IP bloqueadas en el login. Si está vacía, ninguna IP está bloqueada.",
            null, null, false
        )
    );

    private final ConfigMetadata metadata;

    ConfigKey(ConfigMetadata metadata) {
        this.metadata = metadata;
    }

    public ConfigMetadata getMetadata() {
        return metadata;
    }

    public static Optional<ConfigKey> findByName(String name) {
        return Arrays.stream(values())
            .filter(k -> k.name().equals(name))
            .findFirst();
    }
}
