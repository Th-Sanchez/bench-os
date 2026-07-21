package dev.peluci.benchos.application.usecases.customer;

import dev.peluci.benchos.domain.entities.Customer;

/**
 * FindCustomerUseCase
 *
 * Busca clientes por nome, telefone ou e-mail para facilitar a abertura de uma nova OS no balcão.
 */
public interface FindCustomerUseCase {

    Customer execute(FindCustomerCommand command);

}
