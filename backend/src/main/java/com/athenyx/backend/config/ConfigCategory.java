package com.athenyx.backend.config;

public enum ConfigCategory {
    RETENTION("Retención"),
    AI("Inteligencia Artificial"),
    QUOTAS("Cuotas y límites"),
    HEURISTIC("Análisis heurístico"),
    NOTIFICATIONS("Notificaciones"),
    RATE_LIMIT("Rate Limiting"),
    COPY("Copy y contenido"),
    SECURITY("Seguridad");

    private final String label;

    ConfigCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
