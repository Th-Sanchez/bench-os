package com.benchos.api.finance;

import com.benchos.api.order.PaymentMethod;

public enum TransactionPaymentMethod {
    PIX,
    CREDIT_CARD,
    DEBIT_CARD,
    CASH,
    STORE_CREDIT;

    public static TransactionPaymentMethod fromOrderPaymentMethod(PaymentMethod method) {
        return switch (method) {
            case PIX -> PIX;
            case CREDIT_CARD -> CREDIT_CARD;
            case DEBIT_CARD -> DEBIT_CARD;
            case CASH -> CASH;
        };
    }
}
