package com.benchos.api.order.internal.web.dto;

import java.util.UUID;

public record ApprovePublicQuoteRequest(
        UUID quoteOptionId
) {
}
