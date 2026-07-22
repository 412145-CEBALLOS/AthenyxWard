package com.athenyx.backend.exception;

public class CheckoutNotPendingException extends RuntimeException {
    public CheckoutNotPendingException() {
        super("El pago ya no está pendiente. Iniciá un nuevo proceso de pago.");
    }
}
