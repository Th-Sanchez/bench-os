package dev.peluci.benchos.application.usecases.serviceorder;

import java.util.UUID;

/**
 * ApproveServiceOrderCommand
 */
public record ApproveServiceOrderCommand(
    UUID tenantId,
    UUID serviceOrderId
) {
}
