package com.benchos.api.finance.internal.web.dto;

import com.benchos.api.finance.TransactionPaymentMethod;

public record PayTransactionRequest(
        TransactionPaymentMethod paymentMethod
) {
}
