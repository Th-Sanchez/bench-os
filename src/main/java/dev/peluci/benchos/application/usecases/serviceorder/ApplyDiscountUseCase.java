package dev.peluci.benchos.application.usecases.serviceorder;

/**
 * ApplyDiscountUseCase
 *
 * Permite ao técnico conceder um desconto manual, validando para não ultrapassar o valor total, e atualiza o saldo da OS.
 */
public interface ApplyDiscountUseCase {

    void execute(ApplyDiscountCommand command);

}
