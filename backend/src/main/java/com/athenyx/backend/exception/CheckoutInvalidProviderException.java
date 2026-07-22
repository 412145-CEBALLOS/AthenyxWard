package com.athenyx.backend.exception;

public class CheckoutInvalidProviderException extends RuntimeException {
    public CheckoutInvalidProviderException(String provider) {
        super("Proveedor no soportado: " + provider);
    }
}
