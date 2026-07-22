package com.athenyx.backend.exception;

public class CheckoutAlreadyCompletedException extends RuntimeException {
    public CheckoutAlreadyCompletedException() {
        super("Este pago ya fue completado.");
    }
}
