package com.athenyx.backend.payment;

import com.athenyx.backend.entity.PaymentStatus;
import lombok.Getter;

@Getter
public enum MPStatus {
    APPROVED(PaymentStatus.COMPLETED),
    REJECTED(PaymentStatus.FAILED),
    PENDING(PaymentStatus.PENDING),
    IN_PROCESS(PaymentStatus.PENDING),
    CANCELLED(PaymentStatus.FAILED),
    REFUNDED(PaymentStatus.REFUNDED),
    UNKNOWN(PaymentStatus.PENDING);

    private final PaymentStatus paymentStatus;

    MPStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public static MPStatus fromMpString(String mpStatus) {
        if (mpStatus == null) {
            return UNKNOWN;
        }
        return switch (mpStatus.toLowerCase()) {
            case "approved" -> APPROVED;
            case "rejected" -> REJECTED;
            case "pending" -> PENDING;
            case "in_process" -> IN_PROCESS;
            case "cancelled" -> CANCELLED;
            case "refunded" -> REFUNDED;
            default -> UNKNOWN;
        };
    }
}
