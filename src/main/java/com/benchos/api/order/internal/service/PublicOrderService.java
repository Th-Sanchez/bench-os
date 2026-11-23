package com.benchos.api.order.internal.service;

import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.order.internal.entity.ItemPhoto;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.ItemPhotoRepository;
import com.benchos.api.order.internal.repository.ServiceOrderRepository;
import com.benchos.api.order.internal.web.dto.PublicOrderItemPhotoResponse;
import com.benchos.api.order.internal.web.dto.PublicOrderResponse;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicOrderService {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ItemPhotoRepository itemPhotoRepository;
    private final TenantSettingsQuery tenantSettingsQuery;
    private final ObjectStorageService objectStorageService;

    public PublicOrderService(
            ServiceOrderRepository serviceOrderRepository,
            ItemPhotoRepository itemPhotoRepository,
            TenantSettingsQuery tenantSettingsQuery,
            ObjectStorageService objectStorageService
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.itemPhotoRepository = itemPhotoRepository;
        this.tenantSettingsQuery = tenantSettingsQuery;
        this.objectStorageService = objectStorageService;
    }

    @Transactional(readOnly = true)
    public PublicOrderResponse getPublicOrder(UUID publicToken) {
        ServiceOrder order = serviceOrderRepository.findWithItemsByPublicToken(publicToken)
                .orElseThrow(() -> new ResourceNotFoundException("Service order not found"));

        String shopName = tenantSettingsQuery.getShopName(order.getTenantId());

        List<UUID> itemIds = order.getItems().stream().map(ServiceOrderItem::getId).toList();
        Map<UUID, List<PublicOrderItemPhotoResponse>> photosByItemId = itemIds.isEmpty()
                ? Map.of()
                : itemPhotoRepository
                        .findAllByTenantIdAndServiceOrderItemIdInOrderByCreatedAtAsc(order.getTenantId(), itemIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                ItemPhoto::getServiceOrderItemId,
                                Collectors.mapping(
                                        photo -> PublicOrderItemPhotoResponse.from(
                                                photo,
                                                objectStorageService.generateDownloadUrl(photo.getStorageKey())
                                        ),
                                        Collectors.toList()
                                )
                        ));

        return PublicOrderResponse.from(order, shopName, photosByItemId);
    }
}
