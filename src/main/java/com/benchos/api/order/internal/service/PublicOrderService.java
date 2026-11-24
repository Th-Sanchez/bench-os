package com.benchos.api.order.internal.service;

import com.benchos.api.customer.CustomerQuery;
import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.iam.TenantSettingsSnapshot;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.PublicQuoteRespondedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
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
    private final CustomerQuery customerQuery;
    private final ObjectStorageService objectStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public PublicOrderService(
            ServiceOrderRepository serviceOrderRepository,
            ServiceOrderItemRepository serviceOrderItemRepository,
            ItemPhotoRepository itemPhotoRepository,
            TenantSettingsQuery tenantSettingsQuery,
            CustomerQuery customerQuery,
            ObjectStorageService objectStorageService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.itemPhotoRepository = itemPhotoRepository;
        this.tenantSettingsQuery = tenantSettingsQuery;
        this.customerQuery = customerQuery;
        this.objectStorageService = objectStorageService;
        this.eventPublisher = eventPublisher;
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

        Instant now = Instant.now();
        TenantSettingsSnapshot settings = tenantSettingsQuery.getSettings(order.getTenantId());
        LocalDate estimatedDate = LocalDate.now(ZoneOffset.UTC).plusDays(settings.defaultEstimatedDays());
        item.transitionTo(ItemStatus.APPROVED, estimatedDate, null, now);
        serviceOrderItemRepository.save(item);

        publishQuoteRespondedEvent(order, item, true, now);
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

        Instant now = Instant.now();
        item.transitionTo(ItemStatus.REJECTED, null, null, now);
        serviceOrderItemRepository.save(item);

        publishQuoteRespondedEvent(order, item, false, now);
        return buildPublicResponse(order);
    }

    private void publishQuoteRespondedEvent(
            ServiceOrder order,
            ServiceOrderItem item,
            boolean approved,
            Instant respondedAt
    ) {
        String customerName = customerQuery.findActiveById(order.getCustomerId(), order.getTenantId())
                .map(CustomerSnapshot::name)
                .orElse("Cliente");

        eventPublisher.publishEvent(new PublicQuoteRespondedEvent(
                order.getTenantId(),
                order.getId(),
                item.getId(),
                order.getOrderNumber(),
                item.getItemIndex(),
                item.getTag(),
                item.getApplianceDisplayName(),
                customerName,
                approved,
                item.getTotalPrice(),
                respondedAt
        ));
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
