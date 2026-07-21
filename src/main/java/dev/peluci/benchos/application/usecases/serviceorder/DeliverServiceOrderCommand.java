package dev.peluci.benchos.application.usecases.serviceorder;

import java.util.UUID;

/**
 * DeliverServiceOrderCommand
 */
public record DeliverServiceOrderCommand(
    UUID tenantId,
    UUID serviceOrderId
) {
}
