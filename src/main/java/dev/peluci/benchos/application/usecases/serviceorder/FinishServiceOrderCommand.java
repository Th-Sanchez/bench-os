package dev.peluci.benchos.application.usecases.serviceorder;

import java.util.UUID;

/**
 * FinishServiceOrderCommand
 */
public record FinishServiceOrderCommand(
    UUID tenantId,
    UUID serviceOrderId
) {
}
