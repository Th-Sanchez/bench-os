package com.benchos.api.integration.internal.listener;

import com.benchos.api.integration.WebhookEventType;
import com.benchos.api.integration.internal.service.WebhookService;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.OrderCreatedEvent;
import com.benchos.api.order.OrderItemDeliveredEvent;
import com.benchos.api.order.OrderItemStatusChangedEvent;
import com.benchos.api.order.OrderPartArrivedEvent;
import com.benchos.api.order.PublicQuoteRespondedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asynchronous Spring Modulith listener that bridges core workshop domain events
 * to tenant-configured signed outbound webhooks.
 */
@Component
public class WebhookDomainEventListener {

    private final WebhookService webhookService;

    public WebhookDomainEventListener(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @ApplicationModuleListener
    public void onOrderCreated(OrderCreatedEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceOrderId", event.serviceOrderId());
        data.put("orderNumber", event.orderNumber());
        data.put("publicToken", event.publicToken());
        data.put("customerId", event.customerId());
        data.put("customerName", event.customerName());
        data.put("itemCount", event.itemCount());
        data.put("createdAt", event.createdAt());

        webhookService.dispatchEvent(event.tenantId(), WebhookEventType.ORDER_CREATED, data);
    }

    @ApplicationModuleListener
    public void onItemStatusChanged(OrderItemStatusChangedEvent event) {
        WebhookEventType webhookType = switch (event.status()) {
            case QUOTE_SENT -> WebhookEventType.ITEM_QUOTE_SENT;
            case APPROVED -> WebhookEventType.ITEM_APPROVED;
            case READY_FOR_PICKUP -> WebhookEventType.ITEM_READY_FOR_PICKUP;
            default -> null;
        };
        if (webhookType == null) {
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceOrderId", event.serviceOrderId());
        data.put("itemId", event.itemId());
        data.put("orderNumber", event.orderNumber());
        data.put("itemIndex", event.itemIndex());
        data.put("tag", event.tag());
        data.put("appliance", event.applianceLabel());
        data.put("status", event.status().name());
        data.put("totalPrice", event.totalPrice());
        if (event.selectedQuoteOptionLabel() != null) {
            data.put("selectedQuoteOptionLabel", event.selectedQuoteOptionLabel());
        }
        data.put("changedAt", event.changedAt());

        webhookService.dispatchEvent(event.tenantId(), webhookType, data);
    }

    @ApplicationModuleListener
    public void onPublicQuoteResponded(PublicQuoteRespondedEvent event) {
        if (!event.approved()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceOrderId", event.serviceOrderId());
        data.put("itemId", event.itemId());
        data.put("orderNumber", event.orderNumber());
        data.put("itemIndex", event.itemIndex());
        data.put("tag", event.tag());
        data.put("appliance", event.applianceLabel());
        data.put("customerName", event.customerName());
        data.put("status", ItemStatus.APPROVED.name());
        data.put("source", "PUBLIC_TRACK");
        data.put("totalPrice", event.totalPrice());
        data.put("respondedAt", event.respondedAt());

        webhookService.dispatchEvent(event.tenantId(), WebhookEventType.ITEM_APPROVED, data);
    }

    @ApplicationModuleListener
    public void onItemDelivered(OrderItemDeliveredEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceOrderId", event.serviceOrderId());
        data.put("itemId", event.itemId());
        data.put("orderNumber", event.orderNumber());
        data.put("itemIndex", event.itemIndex());
        data.put("tag", event.orderNumber() + "-" + event.itemIndex());
        data.put("totalPrice", event.totalPrice());
        data.put("payments", event.payments());
        data.put("deliveredAt", event.deliveredAt());

        webhookService.dispatchEvent(event.tenantId(), WebhookEventType.ITEM_DELIVERED, data);
    }

    @ApplicationModuleListener
    public void onPartArrived(OrderPartArrivedEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partRequestId", event.partRequestId());
        data.put("serviceOrderId", event.serviceOrderId());
        data.put("itemId", event.itemId());
        data.put("orderNumber", event.orderNumber());
        data.put("itemIndex", event.itemIndex());
        data.put("tag", event.tag());
        data.put("partName", event.partName());
        data.put("quantity", event.quantity());
        data.put("totalCost", event.totalCost());
        if (event.supplierName() != null) {
            data.put("supplierName", event.supplierName());
        }
        data.put("resultingItemStatus", event.resultingItemStatus().name());
        data.put("arrivedAt", event.arrivedAt());

        webhookService.dispatchEvent(event.tenantId(), WebhookEventType.PART_REQUEST_ARRIVED, data);
    }
}
