package dev.peluci.benchos.application.usecases.serviceorder;

import dev.peluci.benchos.domain.entities.ServiceOrder;

/**
 * OpenServiceOrderUseCase
 *
 * Valida limite diário da loja (TenantConfig), associa o cliente e o aparelho, e cria a OS com status RECEIVED.
 */
public interface OpenServiceOrderUseCase {

    ServiceOrder execute(OpenServiceOrderCommand command);

}
