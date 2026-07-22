package com.athenyx.backend.exception;

public class CheckoutNotFoundException extends RuntimeException {
    public CheckoutNotFoundException(Long id) {
        super("Pago no encontrado: " + id);
    }
}
