package dev.peluci.benchos.application.usecases.serviceorder;

/**
 * FinishServiceOrderUseCase
 *
 * O técnico marca o reparo como concluído (READY). Pode disparar um evento para notificar o cliente (WhatsApp).
 */
public interface FinishServiceOrderUseCase {

	void execute(FinishServiceOrderCommand command);
}
