package com.athenyx.backend.payment;

public class MpApiException extends RuntimeException {

    private final Integer statusCode;
    private final String responseBody;

    public MpApiException(String message) {
        super(message);
        this.statusCode = null;
        this.responseBody = null;
    }

    public MpApiException(String message, Integer statusCode, String responseBody) {
        super(message + " (status=" + statusCode + ")");
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public MpApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
        this.responseBody = null;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
