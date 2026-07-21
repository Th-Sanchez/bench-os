package dev.peluci.benchos.application.usecases.customer;

import java.util.UUID;

/**
 * CreateCustomerCommand
 */
public record CreateCustomerCommand(
    UUID tenantId,
    String name,
    String phone,
    String email
) {
}
