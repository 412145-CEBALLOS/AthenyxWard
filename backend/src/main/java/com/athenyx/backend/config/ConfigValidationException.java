package com.athenyx.backend.config;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ConfigValidationException extends RuntimeException {
    private final String field;
    private final String rejectedValue;
    private final HttpStatus status;

    public ConfigValidationException(String field, String rejectedValue, String message) {
        super(message);
        this.field = field;
        this.rejectedValue = rejectedValue;
        this.status = HttpStatus.BAD_REQUEST;
    }

    public ConfigValidationException(String message) {
        super(message);
        this.field = null;
        this.rejectedValue = null;
        this.status = HttpStatus.BAD_REQUEST;
    }
}
