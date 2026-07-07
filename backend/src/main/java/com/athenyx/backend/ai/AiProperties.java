package com.athenyx.backend.ai;

/**
 * Snapshot inmutable de la configuración de IA para inyección limpia en
 * servicios (AiExplanationService en US 3.2).
 *
 * @param enabled   true si el chat está activo; false fuerza fallback siempre.
 * @param modelName nombre del modelo Ollama (ej. "llama3").
 * @param temperature parámetro de creatividad (0.2 = respuestas más
 *                   determinísticas, adecuado para explicar correos).
 * @param numPredict tokens máximos de respuesta (~3 párrafos cortos).
 * @param timeout tiempo máximo de espera por respuesta del modelo.
 */
public record AiProperties(
        boolean enabled,
        String modelName,
        double temperature,
        int numPredict,
        java.time.Duration timeout) {}
