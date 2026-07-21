package dev.peluci.benchos.application.usecases.serviceorder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * ApplyDiscountCommand
 */
public record ApplyDiscountCommand(
    UUID tenantId,
    UUID serviceOrderId,
    BigDecimal discountAmount
) {
}
