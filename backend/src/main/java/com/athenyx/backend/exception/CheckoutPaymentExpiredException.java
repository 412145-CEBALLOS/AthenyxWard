package com.athenyx.backend.exception;

public class CheckoutPaymentExpiredException extends RuntimeException {
    public CheckoutPaymentExpiredException() {
        super("El pago expiró");
    }
}
