package dev.peluci.benchos.application.usecases.customer;

/**
 * UpdateCustomerUseCase
 *
 * Atualiza dados cadastrais de um cliente existente.
 */
public interface UpdateCustomerUseCase {

    void execute(UpdateCustomerCommand command);

}
