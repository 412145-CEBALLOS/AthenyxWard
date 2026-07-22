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
    ),
    SECURITY_TRUST_FORWARDED_HEADERS(
        new ConfigMetadata(
            ConfigCategory.SECURITY,
            ConfigType.BOOLEAN,
            "true",
            "Si es true, el filtro de IP allowlist confía en el header X-Forwarded-For para identificar la IP real del cliente (necesario cuando el backend está detrás de ngrok u otro proxy). En producción, deshabilitar salvo que haya un proxy confiable.",
            null, null, false
        )
    ),
    SECURITY_WEBHOOK_IP_ALLOWLIST_ENABLED(
        new ConfigMetadata(
            ConfigCategory.SECURITY,
            ConfigType.BOOLEAN,
            "false",
            "Habilita el filtro de IP allowlist en el endpoint /api/webhooks/mercadopago. En desarrollo con ngrok, dejar en false (el webhook se autentica via URL secreta). En producción con IP de MP fijas, poner en true.",
            null, null, false
        )
    ),
    SUBSCRIPTION_PRICE_MONTHLY(
        new ConfigMetadata(
            ConfigCategory.PRICING,
            ConfigType.STRING,
            "4999.00",
            "Precio mensual de la suscripción Premium en ARS (formato con punto decimal).",
            null, null, false
        )
    ),
    SUBSCRIPTION_PRICE_ANNUAL(
        new ConfigMetadata(
            ConfigCategory.PRICING,
            ConfigType.STRING,
            "47988.00",
            "Precio anual de la suscripción Premium en ARS. Representa el descuento anual aplicado.",
            null, null, false
        )
    ),
    SUBSCRIPTION_CURRENCY(
        new ConfigMetadata(
            ConfigCategory.PRICING,
            ConfigType.STRING,
            "ARS",
            "Moneda de la suscripción (código ISO 4217). Por ahora solo ARS.",
            null, null, false
        )
    ),
    SUBSCRIPTION_ANNUAL_SAVINGS_PERCENT(
        new ConfigMetadata(
            ConfigCategory.PRICING,
            ConfigType.INT,
            "20",
            "Porcentaje de descuento por contratar el plan anual (mostrado en el badge de la UI).",
            0, 100, false
        )
    ),
    PAYMENT_ENABLED_PROVIDERS(
        new ConfigMetadata(
            ConfigCategory.PRICING,
            ConfigType.STRING,
            "PAYPAL,MERCADOPAGO,CARD",
            "Lista CSV de proveedores de pago activos. Opciones válidas: PAYPAL, MERCADOPAGO, CARD.",
            null, null, false
        )
    ),
    MERCADOPAGO_ENABLED(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.BOOLEAN,
            "true",
            "Habilita el provider real de MercadoPago. Si es false o el token no está configurado, usa stub.",
            null, null, false
        )
    ),
    MERCADOPAGO_ACCESS_TOKEN(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "",
            "Access token de MercadoPago (sandbox o producción). Dejar vacío para usar stub.",
            null, null, false
        )
    ),
    MERCADOPAGO_PUBLIC_KEY(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "",
            "Public key de MercadoPago para uso futuro en el frontend.",
            null, null, false
        )
    ),
    MERCADOPAGO_SANDBOX(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.BOOLEAN,
            "true",
            "Si true, usa el entorno sandbox de MercadoPago.",
            null, null, false
        )
    ),
    MERCADOPAGO_BACK_URL_SUCCESS(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "http://localhost:4200/checkout/return",
            "URL de retorno al frontend cuando el pago en MP fue exitoso.",
            null, null, false
        )
    ),
    MERCADOPAGO_BACK_URL_SUCCESS_BACKEND(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "https://hatchling-pebble-outpost.ngrok-free.dev/api/checkout/return",
            "URL del backend para que MP redirija después de pago exitoso (sin re-login).",
            null, null, false
        )
    ),
    MERCADOPAGO_BACK_URL_FAILURE(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "http://localhost:4200/checkout/return",
            "URL de retorno al frontend cuando el pago en MP falló.",
            null, null, false
        )
    ),
    MERCADOPAGO_BACK_URL_PENDING(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "http://localhost:4200/checkout/return",
            "URL de retorno al frontend cuando el pago en MP quedó pendiente.",
            null, null, false
        )
    ),
    MERCADOPAGO_NOTIFICATION_URL(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "",
            "URL pública (HTTPS) donde MercadoPago envía las notificaciones IPN. Ej: https://tu-dominio.com/api/webhooks/mercadopago",
            null, null, false
        )
    ),
    MERCADOPAGO_IP_ALLOWLIST(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "209.225.49.0/24,216.33.197.0/24,64.7.219.0/24,149.56.151.0/24,149.56.153.0/24,149.56.155.0/24,149.56.157.0/24,149.56.159.0/24",
            "Lista CSV de CIDR permitidos para webhooks de MP. Por defecto: rangos oficiales de MP (incluye rangos legacy y actuales de AWS US-East).",
            null, null, false
        )
    ),
    MERCADOPAGO_API_BASE_URL(
        new ConfigMetadata(
            ConfigCategory.PAYMENT,
            ConfigType.STRING,
            "https://api.mercadopago.com",
            "URL base para llamadas REST a la API de MercadoPago. Por defecto: producción.",
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
