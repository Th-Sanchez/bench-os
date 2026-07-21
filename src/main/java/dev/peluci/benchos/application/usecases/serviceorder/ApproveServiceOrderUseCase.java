package dev.peluci.benchos.application.usecases.serviceorder;

/**
 * ApproveServiceOrderUseCase
 *
 * Registra o aceite do cliente, calcula a data limite (deadlineAt) ignorando finais de semana/feriados, e define como APPROVED.
 */
public interface ApproveServiceOrderUseCase {

    void execute(ApproveServiceOrderCommand command);

}
