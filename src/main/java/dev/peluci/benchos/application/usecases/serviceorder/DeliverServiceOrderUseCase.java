package dev.peluci.benchos.application.usecases.serviceorder;

/**
 * DeliverServiceOrderUseCase
 *
 * Valida se o saldo devedor está zerado (total pago >= total orçado - desconto). Se sim, encerra a OS como DELIVERED.
 */
public interface DeliverServiceOrderUseCase {

    void execute(DeliverServiceOrderCommand command);

}
