package com.benchos.api.order.internal.service;

import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.repository.ServiceOrderRepository;
import com.benchos.api.order.internal.web.dto.PublicOrderResponse;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PublicOrderService {

    private final ServiceOrderRepository serviceOrderRepository;
    private final TenantSettingsQuery tenantSettingsQuery;

    public PublicOrderService(
            ServiceOrderRepository serviceOrderRepository,
            TenantSettingsQuery tenantSettingsQuery
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.tenantSettingsQuery = tenantSettingsQuery;
    }

    @Transactional(readOnly = true)
    public PublicOrderResponse getPublicOrder(UUID publicToken) {
        ServiceOrder order = serviceOrderRepository.findWithItemsByPublicToken(publicToken)
                .orElseThrow(() -> new ResourceNotFoundException("Service order not found"));

        String shopName = tenantSettingsQuery.getShopName(order.getTenantId());
        return PublicOrderResponse.from(order, shopName);
    }
}
