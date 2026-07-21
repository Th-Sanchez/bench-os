package dev.peluci.benchos.application.usecases.customer;

import java.util.UUID;

/**
 * UpdateCustomerCommand
 */
public record UpdateCustomerCommand(
    UUID tenantId,
    UUID customerId,
    String name,
    String phone,
    String email
) {
}
