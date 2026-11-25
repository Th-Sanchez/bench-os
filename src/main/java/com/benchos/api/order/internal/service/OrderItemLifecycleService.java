package com.benchos.api.order.internal.service;

import com.benchos.api.customer.CustomerQuery;
import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.iam.TenantSettingsSnapshot;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.OrderItemDeliveredEvent;
import com.benchos.api.order.PaymentPart;
import com.benchos.api.order.WhatsappMessageStage;
import com.benchos.api.order.internal.config.PublicTrackingProperties;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.order.internal.repository.ServiceOrderRepository;
import com.benchos.api.order.internal.web.dto.DeliverItemRequest;
import com.benchos.api.order.internal.web.dto.ServiceOrderItemResponse;
import com.benchos.api.order.internal.web.dto.TransitionItemStatusRequest;
import com.benchos.api.order.internal.web.dto.UpdateItemQuoteRequest;
import com.benchos.api.order.internal.web.dto.WhatsappLinkResponse;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.security.SecurityUtils;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderItemLifecycleService {

    private static final String FALLBACK_WHATSAPP_INTAKE_TEMPLATE =
            "Olá {customerName}! Registramos sua OS #{orderNumber}:\n{appliances}\nAcompanhe o andamento em tempo real: {trackingUrl}";
    private static final String FALLBACK_WHATSAPP_QUOTE_TEMPLATE =
            "Olá {customerName}! Segue o orçamento da OS #{orderNumber}:\n{appliances}\nTotal: {totalPrice}\nAprove ou acompanhe por aqui: {trackingUrl}";
    private static final String FALLBACK_WHATSAPP_READY_TEMPLATE =
            "Olá {customerName}! Seus aparelhos da OS #{orderNumber} estão prontos para retirada:\n{appliances}\nTotal: {totalPrice}\nDetalhes: {trackingUrl}";

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final Set<ItemStatus> TECHNICAL_ONLY_TARGET_STATUSES = EnumSet.of(
            ItemStatus.DIAGNOSING,
            ItemStatus.QUOTE_SENT,
            ItemStatus.WAITING_PARTS,
            ItemStatus.IN_REPAIR,
            ItemStatus.READY_FOR_PICKUP
    );

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final TenantSettingsQuery tenantSettingsQuery;
    private final CustomerQuery customerQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final PublicTrackingProperties publicTrackingProperties;

    public OrderItemLifecycleService(
            ServiceOrderRepository serviceOrderRepository,
            ServiceOrderItemRepository serviceOrderItemRepository,
            TenantSettingsQuery tenantSettingsQuery,
            CustomerQuery customerQuery,
            ApplicationEventPublisher eventPublisher,
            PublicTrackingProperties publicTrackingProperties
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.tenantSettingsQuery = tenantSettingsQuery;
        this.customerQuery = customerQuery;
        this.eventPublisher = eventPublisher;
        this.publicTrackingProperties = publicTrackingProperties;
    }

    @Transactional
    public ServiceOrderItemResponse updateQuote(UUID orderId, UUID itemId, UpdateItemQuoteRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrderItem item = findItemForUpdateOrThrow(orderId, itemId, tenantId);

        item.updateQuote(
                request.technicalDiagnosis(),
                request.laborCost(),
                request.partsCost(),
                request.discount(),
                request.warrantyDays(),
                Instant.now()
        );

        ServiceOrderItem saved = serviceOrderItemRepository.saveAndFlush(item);
        return toItemResponse(saved, tenantId);
    }

    @Transactional
    public ServiceOrderItemResponse transitionStatus(UUID orderId, UUID itemId, TransitionItemStatusRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        enforceTransitionRolePermission(request.status());
        ServiceOrderItem item = findItemForUpdateOrThrow(orderId, itemId, tenantId);
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

        ServiceOrderItem saved = serviceOrderItemRepository.saveAndFlush(item);
        return toItemResponse(saved, tenantId);
    }

    @Transactional
    public ServiceOrderItemResponse deliverItem(UUID orderId, UUID itemId, DeliverItemRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrderItem item = findItemForUpdateOrThrow(orderId, itemId, tenantId);

        List<PaymentPart> paymentParts = (request == null || request.payments() == null)
                ? List.of()
                : request.payments().stream()
                .map(part -> new PaymentPart(part.paymentMethod(), part.amount()))
                .toList();

        Optional<OrderItemDeliveredEvent> event = item.deliver(paymentParts, Instant.now());
        ServiceOrderItem saved = serviceOrderItemRepository.saveAndFlush(item);

        event.ifPresent(eventPublisher::publishEvent);

        return toItemResponse(saved, tenantId);
    }

    @Transactional(readOnly = true)
    public WhatsappLinkResponse generateOrderWhatsappLink(UUID orderId, WhatsappMessageStage requestedStage) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrder order = serviceOrderRepository.findWithItemsByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order not found"));

        CustomerSnapshot customer = customerQuery.findAllByIds(List.of(order.getCustomerId()), tenantId)
                .get(order.getCustomerId());
        if (customer == null) {
            throw new ResourceNotFoundException("Customer not found");
        }

        List<ServiceOrderItem> sortedItems = order.getItems().stream()
                .sorted(Comparator.comparingInt(ServiceOrderItem::getItemIndex))
                .toList();

        WhatsappMessageStage stage = requestedStage != null ? requestedStage : inferStage(sortedItems);
        TenantSettingsSnapshot settings = tenantSettingsQuery.getSettings(tenantId);
        String template = resolveTemplateForStage(settings, stage);

        String appliancesSummary = sortedItems.stream()
                .map(item -> formatOrderItemLine(item, stage))
                .collect(Collectors.joining("\n"));

        BigDecimal orderTotal = sortedItems.stream()
                .filter(item -> item.getStatus() != ItemStatus.CANCELLED && item.getStatus() != ItemStatus.REJECTED)
                .map(ServiceOrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String trackingUrl = publicTrackingProperties.baseUrl() + "/" + order.getPublicToken();
        String message = template
                .replace("{customerName}", customer.name())
                .replace("{appliances}", appliancesSummary)
                .replace("{appliance}", appliancesSummary)
                .replace("{orderNumber}", String.valueOf(order.getOrderNumber()))
                .replace("{totalPrice}", formatCurrency(orderTotal))
                .replace("{trackingUrl}", trackingUrl);

        String waPhone = formatWhatsappPhone(customer.phone());
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        String whatsappUrl = "https://wa.me/" + waPhone + "?text=" + encodedMessage;

        return new WhatsappLinkResponse(message, whatsappUrl);
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
                ? FALLBACK_WHATSAPP_QUOTE_TEMPLATE
                : settings.whatsappQuoteTemplate();

        String trackingUrl = publicTrackingProperties.baseUrl() + "/" + order.getPublicToken();
        String message = template
                .replace("{customerName}", customer.name())
                .replace("{appliance}", item.getApplianceDisplayName())
                .replace("{appliances}", item.getApplianceDisplayName())
                .replace("{orderNumber}", String.valueOf(order.getOrderNumber()))
                .replace("{tag}", item.getTag())
                .replace("{totalPrice}", formatCurrency(item.getTotalPrice()))
                .replace("{trackingUrl}", trackingUrl);

        String waPhone = formatWhatsappPhone(customer.phone());
        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        String whatsappUrl = "https://wa.me/" + waPhone + "?text=" + encodedMessage;

        return new WhatsappLinkResponse(message, whatsappUrl);
    }

    private static WhatsappMessageStage inferStage(List<ServiceOrderItem> items) {
        boolean anyReady = items.stream().anyMatch(i -> i.getStatus() == ItemStatus.READY_FOR_PICKUP);
        boolean anyQuote = items.stream().anyMatch(i -> i.getStatus() == ItemStatus.QUOTE_SENT);
        if (anyReady && !anyQuote) {
            return WhatsappMessageStage.READY;
        }
        if (anyQuote) {
            return WhatsappMessageStage.QUOTE;
        }
        return WhatsappMessageStage.INTAKE;
    }

    private static String resolveTemplateForStage(TenantSettingsSnapshot settings, WhatsappMessageStage stage) {
        return switch (stage) {
            case INTAKE -> (settings.whatsappIntakeTemplate() == null || settings.whatsappIntakeTemplate().isBlank())
                    ? FALLBACK_WHATSAPP_INTAKE_TEMPLATE
                    : settings.whatsappIntakeTemplate();
            case QUOTE -> (settings.whatsappQuoteTemplate() == null || settings.whatsappQuoteTemplate().isBlank())
                    ? FALLBACK_WHATSAPP_QUOTE_TEMPLATE
                    : settings.whatsappQuoteTemplate();
            case READY -> (settings.whatsappReadyTemplate() == null || settings.whatsappReadyTemplate().isBlank())
                    ? FALLBACK_WHATSAPP_READY_TEMPLATE
                    : settings.whatsappReadyTemplate();
        };
    }

    private static String formatOrderItemLine(ServiceOrderItem item, WhatsappMessageStage stage) {
        String base = "• #" + item.getTag() + " " + item.getApplianceDisplayName();
        boolean hasPrice = item.getTotalPrice() != null && item.getTotalPrice().compareTo(BigDecimal.ZERO) > 0;

        return switch (stage) {
            case INTAKE -> base;
            case QUOTE -> {
                if (item.getStatus() == ItemStatus.REJECTED) {
                    yield base + " — Recusado";
                }
                if (item.getStatus() == ItemStatus.CANCELLED) {
                    yield base + " — Cancelado";
                }
                yield hasPrice
                        ? base + " — " + formatCurrency(item.getTotalPrice())
                        : base + " — Em avaliação";
            }
            case READY -> switch (item.getStatus()) {
                case READY_FOR_PICKUP -> hasPrice
                        ? base + " — Pronto p/ retirada (" + formatCurrency(item.getTotalPrice()) + ")"
                        : base + " — Pronto p/ retirada";
                case DELIVERED -> base + " — Já entregue";
                case REJECTED -> base + " — Devolução sem custo";
                case CANCELLED -> base + " — Cancelado";
                default -> base + " — Em andamento";
            };
        };
    }

    private void enforceTransitionRolePermission(ItemStatus targetStatus) {
        String role = SecurityUtils.requireCurrentUser().role();
        if ("ATTENDANT".equals(role) && targetStatus != null && TECHNICAL_ONLY_TARGET_STATUSES.contains(targetStatus)) {
            throw new AccessDeniedException(
                    "Attendants cannot perform technical status transitions (" + targetStatus + ")"
            );
        }
    }

    private ServiceOrderItemResponse toItemResponse(ServiceOrderItem item, UUID tenantId) {
        UUID parentOrderId = null;
        String parentItemTag = null;
        if (item.getParentItemId() != null) {
            List<ServiceOrderItem> parents = serviceOrderItemRepository.findAllByTenantIdAndIdIn(
                    tenantId,
                    List.of(item.getParentItemId())
            );
            if (!parents.isEmpty()) {
                ServiceOrderItem parent = parents.getFirst();
                parentOrderId = parent.getServiceOrder().getId();
                parentItemTag = parent.getTag();
            }
        }
        return ServiceOrderItemResponse.from(
                item,
                item.getServiceOrder().getOrderNumber(),
                parentOrderId,
                parentItemTag
        );
    }

    private ServiceOrderItem findItemOrThrow(UUID orderId, UUID itemId, UUID tenantId) {
        return serviceOrderItemRepository.findByIdAndServiceOrderIdAndTenantId(itemId, orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order item not found"));
    }

    private ServiceOrderItem findItemForUpdateOrThrow(UUID orderId, UUID itemId, UUID tenantId) {
        return serviceOrderItemRepository.findForUpdateByIdAndServiceOrderIdAndTenantId(itemId, orderId, tenantId)
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
