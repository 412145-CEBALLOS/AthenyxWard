package com.athenyx.backend.exception;

public class CheckoutAlreadyPremiumException extends RuntimeException {
    public CheckoutAlreadyPremiumException() {
        super("Ya tienes Premium activo");
    }
}
