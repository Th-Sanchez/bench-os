package com.benchos.api.order.internal.web.dto;

import jakarta.validation.Valid;

import java.util.List;

public record DeliverItemRequest(
        @Valid List<PaymentPartRequest> payments
) {
}
