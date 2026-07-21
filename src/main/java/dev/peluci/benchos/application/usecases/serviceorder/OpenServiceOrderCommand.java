package dev.peluci.benchos.application.usecases.serviceorder;

import java.util.UUID;

/**
 * OpenServiceOrderCommand
 */
public record OpenServiceOrderCommand(
    UUID tenantId,
    UUID customerId,
    UUID attendantId,
    String deviceModel,
    String deviceVoltage,
    String notes
) {
}
