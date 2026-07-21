package dev.peluci.benchos.application.usecases.serviceorder;

import java.util.UUID;

/**
 * RejectServiceOrderCommand
 */
public record RejectServiceOrderCommand(
    UUID tenantId,
    UUID serviceOrderId,
    boolean discardAuthorized
) {
}
