package com.benchos.api.order.internal.service;

import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.iam.TenantSettingsSnapshot;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.entity.ItemPhoto;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.ItemPhotoRepository;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.order.internal.repository.ServiceOrderRepository;
import com.benchos.api.order.internal.web.dto.PublicOrderItemPhotoResponse;
import com.benchos.api.order.internal.web.dto.PublicOrderResponse;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicOrderService {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final ItemPhotoRepository itemPhotoRepository;
    private final TenantSettingsQuery tenantSettingsQuery;
    private final ObjectStorageService objectStorageService;

    public PublicOrderService(
            ServiceOrderRepository serviceOrderRepository,
            ServiceOrderItemRepository serviceOrderItemRepository,
            ItemPhotoRepository itemPhotoRepository,
            TenantSettingsQuery tenantSettingsQuery,
            ObjectStorageService objectStorageService
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.itemPhotoRepository = itemPhotoRepository;
        this.tenantSettingsQuery = tenantSettingsQuery;
        this.objectStorageService = objectStorageService;
    }

    @Transactional(readOnly = true)
    public PublicOrderResponse getPublicOrder(UUID publicToken) {
        ServiceOrder order = findOrderOrThrow(publicToken);
        return buildPublicResponse(order);
    }

    @Transactional
    public PublicOrderResponse approveItem(UUID publicToken, int itemIndex) {
        ServiceOrder order = findOrderOrThrow(publicToken);
        ServiceOrderItem item = findItemByIndexOrThrow(order, itemIndex);

        if (item.getStatus() != ItemStatus.QUOTE_SENT) {
            throw new BusinessRuleException(
                    "Quote approval is only available while the item is awaiting decision (QUOTE_SENT)"
            );
        }

        TenantSettingsSnapshot settings = tenantSettingsQuery.getSettings(order.getTenantId());
        LocalDate estimatedDate = LocalDate.now(ZoneOffset.UTC).plusDays(settings.defaultEstimatedDays());
        item.transitionTo(ItemStatus.APPROVED, estimatedDate, null, Instant.now());
        serviceOrderItemRepository.save(item);

        return buildPublicResponse(order);
    }

    @Transactional
    public PublicOrderResponse rejectItem(UUID publicToken, int itemIndex) {
        ServiceOrder order = findOrderOrThrow(publicToken);
        ServiceOrderItem item = findItemByIndexOrThrow(order, itemIndex);

        if (item.getStatus() != ItemStatus.QUOTE_SENT) {
            throw new BusinessRuleException(
                    "Quote rejection is only available while the item is awaiting decision (QUOTE_SENT)"
            );
        }

        item.transitionTo(ItemStatus.REJECTED, null, null, Instant.now());
        serviceOrderItemRepository.save(item);

        return buildPublicResponse(order);
    }

    private ServiceOrder findOrderOrThrow(UUID publicToken) {
        return serviceOrderRepository.findWithItemsByPublicToken(publicToken)
                .orElseThrow(() -> new ResourceNotFoundException("Service order not found"));
    }

    private ServiceOrderItem findItemByIndexOrThrow(ServiceOrder order, int itemIndex) {
        return order.getItems().stream()
                .filter(i -> i.getItemIndex() == itemIndex)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Service order item not found"));
    }

    private PublicOrderResponse buildPublicResponse(ServiceOrder order) {
        String shopName = tenantSettingsQuery.getShopName(order.getTenantId());
        String shopPhone = tenantSettingsQuery.getShopPhone(order.getTenantId());

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

        return PublicOrderResponse.from(order, shopName, shopPhone, photosByItemId);
    }
}
