package com.benchos.api.order.internal.service;

import com.benchos.api.customer.CustomerQuery;
import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.iam.TenantSettingsSnapshot;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.OrderItemDeliveredEvent;
import com.benchos.api.order.PaymentPart;
import com.benchos.api.order.internal.config.PublicTrackingProperties;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.order.internal.web.dto.DeliverItemRequest;
import com.benchos.api.order.internal.web.dto.ServiceOrderItemResponse;
import com.benchos.api.order.internal.web.dto.TransitionItemStatusRequest;
import com.benchos.api.order.internal.web.dto.UpdateItemQuoteRequest;
import com.benchos.api.order.internal.web.dto.WhatsappLinkResponse;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderItemLifecycleService {

    private static final String FALLBACK_WHATSAPP_TEMPLATE =
            "Hello {customerName}, here is the repair quote for your {appliance} (Order #{orderNumber}, tag {tag}): {totalPrice}. Track status: {trackingUrl}";
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final TenantSettingsQuery tenantSettingsQuery;
    private final CustomerQuery customerQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final PublicTrackingProperties publicTrackingProperties;

    public OrderItemLifecycleService(
            ServiceOrderItemRepository serviceOrderItemRepository,
            TenantSettingsQuery tenantSettingsQuery,
            CustomerQuery customerQuery,
            ApplicationEventPublisher eventPublisher,
            PublicTrackingProperties publicTrackingProperties
    ) {
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.tenantSettingsQuery = tenantSettingsQuery;
        this.customerQuery = customerQuery;
        this.eventPublisher = eventPublisher;
        this.publicTrackingProperties = publicTrackingProperties;
    }

    @Transactional
    public ServiceOrderItemResponse updateQuote(UUID orderId, UUID itemId, UpdateItemQuoteRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrderItem item = findItemOrThrow(orderId, itemId, tenantId);

        item.updateQuote(
                request.technicalDiagnosis(),
                request.laborCost(),
                request.partsCost(),
                request.discount(),
                request.warrantyDays(),
                Instant.now()
        );

        ServiceOrderItem saved = serviceOrderItemRepository.save(item);
        return ServiceOrderItemResponse.from(saved, saved.getServiceOrder().getOrderNumber());
    }

    @Transactional
    public ServiceOrderItemResponse transitionStatus(UUID orderId, UUID itemId, TransitionItemStatusRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrderItem item = findItemOrThrow(orderId, itemId, tenantId);
        Instant now = Instant.now();

        if (request.status() == ItemStatus.DISCARDED) {
            TenantSettingsSnapshot settings = tenantSettingsQuery.getSettings(tenantId);
            item.markDiscarded(settings.abandonmentDays(), now);
        } else {
            LocalDate estimatedDate = null;
            if (request.status() == ItemStatus.APPROVED) {
                TenantSettingsSnapshot settings = tenantSettingsQuery.getSettings(tenantId);
                estimatedDate = LocalDate.now(ZoneOffset.UTC).plusDays(settings.defaultEstimatedDays());
            }
            item.transitionTo(request.status(), estimatedDate, request.cancellationReason(), now);
        }

        ServiceOrderItem saved = serviceOrderItemRepository.save(item);
        return ServiceOrderItemResponse.from(saved, saved.getServiceOrder().getOrderNumber());
    }

    @Transactional
    public ServiceOrderItemResponse deliverItem(UUID orderId, UUID itemId, DeliverItemRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrderItem item = findItemOrThrow(orderId, itemId, tenantId);

        List<PaymentPart> paymentParts = (request == null || request.payments() == null)
                ? List.of()
                : request.payments().stream()
                .map(part -> new PaymentPart(part.paymentMethod(), part.amount()))
                .toList();

        Optional<OrderItemDeliveredEvent> event = item.deliver(paymentParts, Instant.now());
        ServiceOrderItem saved = serviceOrderItemRepository.save(item);

        event.ifPresent(eventPublisher::publishEvent);

        return ServiceOrderItemResponse.from(saved, saved.getServiceOrder().getOrderNumber());
    }

    @Transactional(readOnly = true)
    public WhatsappLinkResponse generateWhatsappLink(UUID orderId, UUID itemId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrderItem item = findItemOrThrow(orderId, itemId, tenantId);
        ServiceOrder order = item.getServiceOrder();

        CustomerSnapshot customer = customerQuery.findAllByIds(List.of(order.getCustomerId()), tenantId)
                .get(order.getCustomerId());
        if (customer == null) {
            throw new ResourceNotFoundException("Customer not found");
        }

        TenantSettingsSnapshot settings = tenantSettingsQuery.getSettings(tenantId);
        String template = (settings.whatsappQuoteTemplate() == null || settings.whatsappQuoteTemplate().isBlank())
                ? FALLBACK_WHATSAPP_TEMPLATE
                : settings.whatsappQuoteTemplate();

        String trackingUrl = publicTrackingProperties.baseUrl() + "/" + order.getPublicToken();
        String message = template
                .replace("{customerName}", customer.name())
                .replace("{appliance}", item.getApplianceDisplayName())
                .replace("{orderNumber}", String.valueOf(order.getOrderNumber()))
                .replace("{tag}", item.getTag())
                .replace("{totalPrice}", formatCurrency(item.getTotalPrice()))
                .replace("{trackingUrl}", trackingUrl);

        String waPhone = formatWhatsappPhone(customer.phone());
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        String whatsappUrl = "https://wa.me/" + waPhone + "?text=" + encodedMessage;

        return new WhatsappLinkResponse(message, whatsappUrl);
    }

    private ServiceOrderItem findItemOrThrow(UUID orderId, UUID itemId, UUID tenantId) {
        return serviceOrderItemRepository.findByIdAndServiceOrderIdAndTenantId(itemId, orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order item not found"));
    }

    private static String formatCurrency(BigDecimal amount) {
        return "R$ " + String.format(PT_BR, "%,.2f", amount);
    }

    private static String formatWhatsappPhone(String normalizedPhone) {
        if ((normalizedPhone.length() == 10 || normalizedPhone.length() == 11) && !normalizedPhone.startsWith("55")) {
            return "55" + normalizedPhone;
        }
        return normalizedPhone;
    }
}
