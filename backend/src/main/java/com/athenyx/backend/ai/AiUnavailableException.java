package com.athenyx.backend.ai;

/**
 * Marker exception que el servicio AI lanza cuando Ollama no responde,
 * la respuesta es inválida, o el chat está deshabilitado. Capturada
 * internamente por AiExplanationService (US 3.2) para devolver un
 * fallback heurístico en lugar de propagar un 5xx al usuario.
 *
 * <p>Esta excepción NO debe propagar al controlador global; debe ser
 * interceptada en la capa de servicio. Si llega a {@code
 * GlobalExceptionHandler} se mapearía a un error 5xx genérico.
 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
