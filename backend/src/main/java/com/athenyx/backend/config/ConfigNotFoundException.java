package com.athenyx.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ConfigNotFoundException extends RuntimeException {
    public ConfigNotFoundException(String key) {
        super("Clave de configuración no encontrada: " + key);
    }
}
