package com.benchos.api.order.internal.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DeliverItemRequest(
        @Valid List<PaymentPartRequest> payments,
        String deliverySignatureSvg,
        @Size(max = 120) String deliverySignerName
) {
    public DeliverItemRequest(List<PaymentPartRequest> payments) {
        this(payments, null, null);
    }
}
