package dev.peluci.benchos.application.usecases.serviceorder;

/**
 * RejectServiceOrderUseCase
 *
 * Registra a recusa. Se o cliente autorizou o descarte (discardAuthorized), move para DISCARDED. Senão, move para REJECTED.
 */
public interface RejectServiceOrderUseCase {

    void execute(RejectServiceOrderCommand command);

}
