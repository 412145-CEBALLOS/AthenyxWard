package com.athenyx.backend.exception;

public class MercadoPagoApiException extends RuntimeException {
    private final int statusCode;
    private final String apiResponseBody;

    public MercadoPagoApiException(int statusCode, String apiResponseBody) {
        super("No se pudo crear la sesion de pago con MercadoPago. Reintentá en unos minutos.");
        this.statusCode = statusCode;
        this.apiResponseBody = apiResponseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getApiResponseBody() {
        return apiResponseBody;
    }
}
