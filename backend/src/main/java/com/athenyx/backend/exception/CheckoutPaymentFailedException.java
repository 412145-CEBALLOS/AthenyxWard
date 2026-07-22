package com.athenyx.backend.exception;

public class CheckoutPaymentFailedException extends RuntimeException {
    public CheckoutPaymentFailedException(String reason) {
        super("Pago rechazado: " + reason);
    }
}
