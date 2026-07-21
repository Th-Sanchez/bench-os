package dev.peluci.benchos.application.usecases.customer;

import dev.peluci.benchos.domain.entities.Customer;

/**
 * CreateCustomerUseCase
 *
 * Cadastra um novo cliente, garantindo que o telefone ou e-mail seja único dentro daquele tenant_id.
 */
public interface CreateCustomerUseCase {

    Customer execute(CreateCustomerCommand command);

}
