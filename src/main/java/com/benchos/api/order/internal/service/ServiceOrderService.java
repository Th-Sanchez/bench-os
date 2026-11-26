package com.benchos.api.order.internal.service;

import com.benchos.api.customer.CustomerQuery;
import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.iam.CommissionBasis;
import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.iam.TenantSettingsSnapshot;
import com.benchos.api.iam.UserDirectoryQuery;
import com.benchos.api.iam.UserSnapshot;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.entity.QuoteOption;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.QuoteOptionRepository;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.order.internal.repository.ServiceOrderRepository;
import com.benchos.api.order.internal.web.dto.CreateServiceOrderItemRequest;
import com.benchos.api.order.internal.web.dto.CreateServiceOrderRequest;
import com.benchos.api.order.internal.web.dto.CreateWarrantyReturnRequest;
import com.benchos.api.order.internal.web.dto.MonthlyCommissionReportResponse;
import com.benchos.api.order.internal.web.dto.OrderItemQueueResponse;
import com.benchos.api.order.internal.web.dto.QuoteOptionResponse;
import com.benchos.api.order.internal.web.dto.RepairProfitabilityResponse;
import com.benchos.api.order.internal.web.dto.ServiceOrderResponse;
import com.benchos.api.order.internal.web.dto.WorkshopAnalyticsResponse;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.security.SecurityUtils;
import com.benchos.api.shared.tenant.TenantContext;
import com.benchos.api.shared.web.PageResponse;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ServiceOrderService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern TAG_PATTERN = Pattern.compile("^(\\d+)(?:-(\\d+))?$");

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final QuoteOptionRepository quoteOptionRepository;
    private final TenantSequenceService tenantSequenceService;
    private final CustomerQuery customerQuery;
    private final TenantSettingsQuery tenantSettingsQuery;
    private final UserDirectoryQuery userDirectoryQuery;

    public ServiceOrderService(
            ServiceOrderRepository serviceOrderRepository,
            ServiceOrderItemRepository serviceOrderItemRepository,
            QuoteOptionRepository quoteOptionRepository,
            TenantSequenceService tenantSequenceService,
            CustomerQuery customerQuery,
            TenantSettingsQuery tenantSettingsQuery,
            UserDirectoryQuery userDirectoryQuery
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.quoteOptionRepository = quoteOptionRepository;
        this.tenantSequenceService = tenantSequenceService;
        this.customerQuery = customerQuery;
        this.tenantSettingsQuery = tenantSettingsQuery;
        this.userDirectoryQuery = userDirectoryQuery;
    }

    @Transactional
    public ServiceOrderResponse createOrder(CreateServiceOrderRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        UUID currentUserId = SecurityUtils.requireCurrentUserId();

        CustomerSnapshot customer = customerQuery.findActiveById(request.customerId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

        long orderNumber = tenantSequenceService.nextOrderNumber(tenantId);
        ServiceOrder order = new ServiceOrder(tenantId, customer.id(), currentUserId, orderNumber, request.notes());

        for (CreateServiceOrderItemRequest itemReq : request.items()) {
            order.addItem(
                    itemReq.applianceType(),
                    itemReq.brand(),
                    itemReq.model(),
                    itemReq.voltage(),
                    itemReq.serialNumber(),
                    itemReq.accessories(),
                    itemReq.reportedIssue(),
                    itemReq.intakeChecklist()
            );
        }

        ServiceOrder savedOrder = serviceOrderRepository.save(order);
        return ServiceOrderResponse.from(savedOrder, customer, savedOrder.getItems());
    }

    @Transactional
    public ServiceOrderResponse openWarrantyReturn(
            UUID parentOrderId,
            UUID parentItemId,
            CreateWarrantyReturnRequest request
    ) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        UUID currentUserId = SecurityUtils.requireCurrentUserId();

        ServiceOrderItem parentItem = serviceOrderItemRepository
                .findByIdAndServiceOrderIdAndTenantId(parentItemId, parentOrderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order item not found"));

        if (parentItem.getStatus() != ItemStatus.DELIVERED) {
            throw new BusinessRuleException("Warranty return can only be opened for delivered items");
        }

        UUID customerId = parentItem.getServiceOrder().getCustomerId();
        CustomerSnapshot customer = customerQuery.findAllByIds(List.of(customerId), tenantId).get(customerId);
        if (customer == null) {
            throw new ResourceNotFoundException("Customer not found");
        }

        int remainingDays = parentItem.remainingWarrantyDays(Instant.now());
        long orderNumber = tenantSequenceService.nextOrderNumber(tenantId);
        ServiceOrder returnOrder = new ServiceOrder(tenantId, customerId, currentUserId, orderNumber, request.notes());
        returnOrder.addWarrantyReturnItem(parentItem, request.reportedIssue(), request.accessories(), remainingDays);

        ServiceOrder savedOrder = serviceOrderRepository.save(returnOrder);
        Map<UUID, ServiceOrderResponse.ParentItemRef> parentRefs = Map.of(
                parentItem.getId(),
                new ServiceOrderResponse.ParentItemRef(parentOrderId, parentItem.getTag())
        );
        return ServiceOrderResponse.from(savedOrder, customer, savedOrder.getItems(), parentRefs);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceOrderResponse> listOrders(
            Long orderNumber,
            UUID customerId,
            ItemStatus status,
            int page,
            int size
    ) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampPageSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderNumber"))
        );

        Specification<ServiceOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (orderNumber != null) {
                predicates.add(cb.equal(root.get("orderNumber"), orderNumber));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                Subquery<UUID> itemSubquery = query.subquery(UUID.class);
                Root<ServiceOrderItem> itemRoot = itemSubquery.from(ServiceOrderItem.class);
                itemSubquery.select(itemRoot.get("id"))
                        .where(
                                cb.equal(itemRoot.get("serviceOrder"), root),
                                cb.equal(itemRoot.get("tenantId"), tenantId),
                                cb.equal(itemRoot.get("status"), status)
                        );
                predicates.add(cb.exists(itemSubquery));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<ServiceOrder> ordersPage = serviceOrderRepository.findAll(spec, pageable);
        if (ordersPage.isEmpty()) {
            return PageResponse.of(List.of(), ordersPage);
        }

        List<UUID> orderIds = ordersPage.getContent().stream().map(ServiceOrder::getId).toList();
        Set<UUID> customerIds = ordersPage.getContent().stream().map(ServiceOrder::getCustomerId).collect(Collectors.toSet());

        List<ServiceOrderItem> allItems = serviceOrderItemRepository
                .findAllByTenantIdAndServiceOrderIdInOrderByServiceOrderIdAscItemIndexAsc(tenantId, orderIds);
        Map<UUID, List<ServiceOrderItem>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getServiceOrder().getId()));
        Map<UUID, ServiceOrderResponse.ParentItemRef> parentRefs = resolveParentItemRefs(tenantId, allItems);
        Map<UUID, String> technicianNames = resolveTechnicianNames(tenantId, allItems);
        Map<UUID, List<QuoteOptionResponse>> quoteOptionsByItemId = resolveQuoteOptions(tenantId, allItems);
        boolean includeCommission = isCurrentUserOwner();

        Map<UUID, CustomerSnapshot> customersById = customerQuery.findAllByIds(customerIds, tenantId);

        List<ServiceOrderResponse> responses = ordersPage.getContent().stream()
                .map(order -> ServiceOrderResponse.from(
                        order,
                        customersById.get(order.getCustomerId()),
                        itemsByOrderId.getOrDefault(order.getId(), List.of()),
                        parentRefs,
                        technicianNames,
                        includeCommission,
                        quoteOptionsByItemId
                ))
                .toList();

        return PageResponse.of(responses, ordersPage);
    }

    @Transactional(readOnly = true)
    public ServiceOrderResponse getOrder(UUID orderId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrder order = serviceOrderRepository.findWithItemsByIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order not found"));

        CustomerSnapshot customer = customerQuery.findAllByIds(List.of(order.getCustomerId()), tenantId)
                .get(order.getCustomerId());
        Map<UUID, ServiceOrderResponse.ParentItemRef> parentRefs = resolveParentItemRefs(tenantId, order.getItems());
        Map<UUID, String> technicianNames = resolveTechnicianNames(tenantId, order.getItems());
        Map<UUID, List<QuoteOptionResponse>> quoteOptionsByItemId = resolveQuoteOptions(tenantId, order.getItems());

        return ServiceOrderResponse.from(
                order,
                customer,
                order.getItems(),
                parentRefs,
                technicianNames,
                isCurrentUserOwner(),
                quoteOptionsByItemId
        );
    }

    @Transactional(readOnly = true)
    public ServiceOrderResponse getOrderByPublicToken(UUID publicToken) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrder order = serviceOrderRepository.findWithItemsByPublicTokenAndTenantId(publicToken, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order not found"));

        CustomerSnapshot customer = customerQuery.findAllByIds(List.of(order.getCustomerId()), tenantId)
                .get(order.getCustomerId());
        Map<UUID, ServiceOrderResponse.ParentItemRef> parentRefs = resolveParentItemRefs(tenantId, order.getItems());
        Map<UUID, String> technicianNames = resolveTechnicianNames(tenantId, order.getItems());
        Map<UUID, List<QuoteOptionResponse>> quoteOptionsByItemId = resolveQuoteOptions(tenantId, order.getItems());

        return ServiceOrderResponse.from(
                order,
                customer,
                order.getItems(),
                parentRefs,
                technicianNames,
                isCurrentUserOwner(),
                quoteOptionsByItemId
        );
    }

    @Transactional(readOnly = true)
    public RepairProfitabilityResponse getRepairProfitability(LocalDate from, LocalDate to, String zone) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ZoneId zoneId;
        if (zone == null || zone.isBlank()) {
            zoneId = ZoneId.of("America/Sao_Paulo");
        } else {
            try {
                zoneId = ZoneId.of(zone.trim());
            } catch (DateTimeException ex) {
                throw new BusinessRuleException("Invalid timezone: " + zone);
            }
        }

        LocalDate today = LocalDate.now(zoneId);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new BusinessRuleException("'to' date must be on or after 'from' date");
        }

        Instant startInclusive = start.atStartOfDay(zoneId).toInstant();
        Instant endExclusive = end.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<ServiceOrderItem> deliveredItems = serviceOrderItemRepository
                .findDeliveredInPeriod(tenantId, startInclusive, endExclusive);

        BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPartsCostActual = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int itemsWithPartsCostCount = 0;

        for (ServiceOrderItem item : deliveredItems) {
            totalRevenue = totalRevenue.add(item.getTotalPrice());
            BigDecimal actualCost = item.getPartsCostActual();
            totalPartsCostActual = totalPartsCostActual.add(actualCost);
            if (actualCost.compareTo(BigDecimal.ZERO) > 0) {
                itemsWithPartsCostCount++;
            }
        }

        BigDecimal estimatedNetProfit = totalRevenue.subtract(totalPartsCostActual)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal profitMarginPercent = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? estimatedNetProfit
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new RepairProfitabilityResponse(
                start,
                end,
                zoneId.getId(),
                deliveredItems.size(),
                itemsWithPartsCostCount,
                totalRevenue.setScale(2, RoundingMode.HALF_UP),
                totalPartsCostActual.setScale(2, RoundingMode.HALF_UP),
                estimatedNetProfit,
                profitMarginPercent
        );
    }

    @Transactional(readOnly = true)
    public WorkshopAnalyticsResponse getWorkshopAnalytics(LocalDate from, LocalDate to, String zone) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ZoneId zoneId;
        if (zone == null || zone.isBlank()) {
            zoneId = ZoneId.of("America/Sao_Paulo");
        } else {
            try {
                zoneId = ZoneId.of(zone.trim());
            } catch (DateTimeException ex) {
                throw new BusinessRuleException("Invalid timezone: " + zone);
            }
        }

        LocalDate today = LocalDate.now(zoneId);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new BusinessRuleException("'to' date must be on or after 'from' date");
        }

        Instant startInclusive = start.atStartOfDay(zoneId).toInstant();
        Instant endExclusive = end.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<ServiceOrderItem> periodItems = serviceOrderItemRepository
                .findForAnalyticsPeriod(tenantId, startInclusive, endExclusive);

        long quotesSentCount = 0;
        long quotesApprovedCount = 0;
        long quotesRejectedCount = 0;
        long quotesPendingCount = 0;

        long intakeToQuoteTotalMinutes = 0;
        long intakeToQuoteSamples = 0;
        long approvalToReadyTotalMinutes = 0;
        long approvalToReadySamples = 0;
        long totalTurnaroundMinutes = 0;
        long totalTurnaroundSamples = 0;

        long deliveredItemsCount = 0;
        long paidRepairsCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPartsCostActual = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        long itemsReceivedCount = 0;
        long warrantyReturnsCount = 0;

        for (ServiceOrderItem item : periodItems) {
            boolean cancelled = item.getStatus() == ItemStatus.CANCELLED;
            boolean freeWarrantyFastTrack = item.isWarrantyReturn()
                    && item.getQuoteSentAt() == null
                    && item.getTotalPrice().compareTo(BigDecimal.ZERO) == 0;

            // 1. Intake & Warranty Return Rate
            if (!cancelled && isInRange(item.getCreatedAt(), startInclusive, endExclusive)) {
                itemsReceivedCount++;
                if (item.isWarrantyReturn()) {
                    warrantyReturnsCount++;
                }
            }

            // 2. Quote Conversion Funnel
            if (!cancelled && !freeWarrantyFastTrack) {
                Instant effectiveQuoteSent = item.getQuoteSentAt();
                boolean sentInPeriod = isInRange(effectiveQuoteSent, startInclusive, endExclusive)
                        || (effectiveQuoteSent == null
                            && (isInRange(item.getApprovedAt(), startInclusive, endExclusive)
                                || isInRange(item.getRejectedAt(), startInclusive, endExclusive)
                                || (item.getStatus() == ItemStatus.QUOTE_SENT
                                    && isInRange(item.getUpdatedAt(), startInclusive, endExclusive))));
                if (sentInPeriod) {
                    quotesSentCount++;
                }

                if (isInRange(item.getApprovedAt(), startInclusive, endExclusive)) {
                    quotesApprovedCount++;
                }

                boolean rejectedInPeriod = isInRange(item.getRejectedAt(), startInclusive, endExclusive)
                        || (item.getRejectedAt() == null
                            && item.getStatus() == ItemStatus.REJECTED
                            && isInRange(item.getUpdatedAt(), startInclusive, endExclusive));
                if (rejectedInPeriod) {
                    quotesRejectedCount++;
                }

                if (item.getStatus() == ItemStatus.QUOTE_SENT) {
                    Instant ref = effectiveQuoteSent != null ? effectiveQuoteSent : item.getUpdatedAt();
                    if (isInRange(ref, startInclusive, endExclusive)) {
                        quotesPendingCount++;
                    }
                }
            }

            // 3. SLA / Turnaround Times
            if (!cancelled
                    && item.getQuoteSentAt() != null
                    && isInRange(item.getQuoteSentAt(), startInclusive, endExclusive)
                    && !item.getQuoteSentAt().isBefore(item.getCreatedAt())) {
                intakeToQuoteTotalMinutes += Duration.between(item.getCreatedAt(), item.getQuoteSentAt()).toMinutes();
                intakeToQuoteSamples++;
            }

            if (!cancelled
                    && item.getFinishedAt() != null
                    && isInRange(item.getFinishedAt(), startInclusive, endExclusive)) {
                if (item.getApprovedAt() != null && !item.getFinishedAt().isBefore(item.getApprovedAt())) {
                    approvalToReadyTotalMinutes += Duration.between(item.getApprovedAt(), item.getFinishedAt()).toMinutes();
                    approvalToReadySamples++;
                }
                if (!item.getFinishedAt().isBefore(item.getCreatedAt())) {
                    totalTurnaroundMinutes += Duration.between(item.getCreatedAt(), item.getFinishedAt()).toMinutes();
                    totalTurnaroundSamples++;
                }
            }

            // 4. Delivered Financial Metrics & Average Ticket
            if (item.getStatus() == ItemStatus.DELIVERED
                    && isInRange(item.getDeliveredAt(), startInclusive, endExclusive)) {
                deliveredItemsCount++;
                BigDecimal price = item.getTotalPrice();
                totalRevenue = totalRevenue.add(price);
                totalPartsCostActual = totalPartsCostActual.add(item.getPartsCostActual());
                if (price.compareTo(BigDecimal.ZERO) > 0) {
                    paidRepairsCount++;
                }
            }
        }

        long decidedQuotes = quotesApprovedCount + quotesRejectedCount;
        BigDecimal quoteApprovalRatePercent = decidedQuotes > 0
                ? BigDecimal.valueOf(quotesApprovedCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(decidedQuotes), 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal avgIntakeToQuoteHours = intakeToQuoteSamples > 0
                ? BigDecimal.valueOf(intakeToQuoteTotalMinutes)
                        .divide(BigDecimal.valueOf(intakeToQuoteSamples * 60L), 1, RoundingMode.HALF_UP)
                : null;

        BigDecimal avgApprovalToReadyHours = approvalToReadySamples > 0
                ? BigDecimal.valueOf(approvalToReadyTotalMinutes)
                        .divide(BigDecimal.valueOf(approvalToReadySamples * 60L), 1, RoundingMode.HALF_UP)
                : null;

        BigDecimal avgTotalTurnaroundHours = totalTurnaroundSamples > 0
                ? BigDecimal.valueOf(totalTurnaroundMinutes)
                        .divide(BigDecimal.valueOf(totalTurnaroundSamples * 60L), 1, RoundingMode.HALF_UP)
                : null;

        BigDecimal netRepairProfit = totalRevenue.subtract(totalPartsCostActual).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netProfitMarginPercent = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? netRepairProfit.multiply(BigDecimal.valueOf(100))
                        .divide(totalRevenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal averageTicket = paidRepairsCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(paidRepairsCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal warrantyReturnRatePercent = itemsReceivedCount > 0
                ? BigDecimal.valueOf(warrantyReturnsCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(itemsReceivedCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 5. Current Live Operational Bottlenecks Snapshot
        List<ServiceOrderItem> activeItems = serviceOrderItemRepository.findAllByTenantIdAndStatusIn(
                tenantId,
                List.of(
                        ItemStatus.RECEIVED,
                        ItemStatus.DIAGNOSING,
                        ItemStatus.QUOTE_SENT,
                        ItemStatus.APPROVED,
                        ItemStatus.WAITING_PARTS,
                        ItemStatus.IN_REPAIR,
                        ItemStatus.READY_FOR_PICKUP
                )
        );

        long overdueActiveCount = 0;
        long awaitingQuoteCount = 0;
        long awaitingApprovalCount = 0;
        long waitingPartsCount = 0;
        long readyForPickupCount = 0;

        for (ServiceOrderItem active : activeItems) {
            switch (active.getStatus()) {
                case RECEIVED, DIAGNOSING -> awaitingQuoteCount++;
                case QUOTE_SENT -> awaitingApprovalCount++;
                case WAITING_PARTS -> {
                    waitingPartsCount++;
                    if (active.getEstimatedCompletionDate() != null
                            && active.getEstimatedCompletionDate().isBefore(today)) {
                        overdueActiveCount++;
                    }
                }
                case APPROVED, IN_REPAIR -> {
                    if (active.getEstimatedCompletionDate() != null
                            && active.getEstimatedCompletionDate().isBefore(today)) {
                        overdueActiveCount++;
                    }
                }
                case READY_FOR_PICKUP -> readyForPickupCount++;
                default -> {
                    // Ignored
                }
            }
        }

        return new WorkshopAnalyticsResponse(
                start,
                end,
                zoneId.getId(),
                quotesSentCount,
                quotesApprovedCount,
                quotesRejectedCount,
                quotesPendingCount,
                quoteApprovalRatePercent,
                avgIntakeToQuoteHours,
                intakeToQuoteSamples,
                avgApprovalToReadyHours,
                approvalToReadySamples,
                avgTotalTurnaroundHours,
                deliveredItemsCount,
                paidRepairsCount,
                totalRevenue.setScale(2, RoundingMode.HALF_UP),
                totalPartsCostActual.setScale(2, RoundingMode.HALF_UP),
                netRepairProfit,
                netProfitMarginPercent,
                averageTicket,
                itemsReceivedCount,
                warrantyReturnsCount,
                warrantyReturnRatePercent,
                overdueActiveCount,
                awaitingQuoteCount,
                awaitingApprovalCount,
                waitingPartsCount,
                readyForPickupCount
        );
    }

    private static boolean isInRange(Instant value, Instant startInclusive, Instant endExclusive) {
        return value != null && !value.isBefore(startInclusive) && value.isBefore(endExclusive);
    }

    @Transactional(readOnly = true)
    public MonthlyCommissionReportResponse getMonthlyCommissionReport(LocalDate from, LocalDate to, String zone) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ZoneId zoneId;
        if (zone == null || zone.isBlank()) {
            zoneId = ZoneId.of("America/Sao_Paulo");
        } else {
            try {
                zoneId = ZoneId.of(zone.trim());
            } catch (DateTimeException ex) {
                throw new BusinessRuleException("Invalid timezone: " + zone);
            }
        }

        LocalDate today = LocalDate.now(zoneId);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today.withDayOfMonth(today.lengthOfMonth());
        if (end.isBefore(start)) {
            throw new BusinessRuleException("'to' date must be on or after 'from' date");
        }

        Instant startInclusive = start.atStartOfDay(zoneId).toInstant();
        Instant endExclusive = end.plusDays(1).atStartOfDay(zoneId).toInstant();

        TenantSettingsSnapshot settings = tenantSettingsQuery.getSettings(tenantId);
        CommissionBasis basis = settings.commissionBasis() != null
                ? settings.commissionBasis()
                : CommissionBasis.LABOR_ONLY;

        List<ServiceOrderItem> deliveredItems = serviceOrderItemRepository
                .findDeliveredInPeriod(tenantId, startInclusive, endExclusive);

        List<ServiceOrderItem> paidDelivered = deliveredItems.stream()
                .filter(item -> item.getTotalPrice().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        Set<UUID> techIds = paidDelivered.stream()
                .map(ServiceOrderItem::getAssignedTechnicianId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, UserSnapshot> techSnapshots = userDirectoryQuery.findAllByIds(techIds, tenantId);

        long deliveredWithTechnicianCount = 0;
        long deliveredUnassignedCount = 0;
        BigDecimal totalCommissionableRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCommissionAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Map<UUID, List<ServiceOrderItem>> itemsByTechId = new java.util.LinkedHashMap<>();
        for (ServiceOrderItem item : paidDelivered) {
            if (item.getAssignedTechnicianId() == null) {
                deliveredUnassignedCount++;
            } else {
                deliveredWithTechnicianCount++;
                itemsByTechId.computeIfAbsent(item.getAssignedTechnicianId(), k -> new ArrayList<>()).add(item);
            }
        }

        List<MonthlyCommissionReportResponse.TechnicianCommissionRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, List<ServiceOrderItem>> entry : itemsByTechId.entrySet()) {
            UUID techId = entry.getKey();
            UserSnapshot tech = techSnapshots.get(techId);
            BigDecimal defaultPct = tech != null ? tech.commissionPercent() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal techLabor = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal techRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal techNetProfit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal techCommission = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            List<MonthlyCommissionReportResponse.CommissionItemDetail> details = new ArrayList<>();

            for (ServiceOrderItem item : entry.getValue()) {
                BigDecimal effectivePct = item.getCommissionPercent() != null
                        ? item.getCommissionPercent()
                        : defaultPct;
                BigDecimal itemCommission = item.getCommissionAmount() != null
                        ? item.getCommissionAmount()
                        : item.calculateCommissionAmount(effectivePct, basis);
                BigDecimal netProfit = item.getEstimatedNetProfit();

                techLabor = techLabor.add(item.getLaborCost());
                techRevenue = techRevenue.add(item.getTotalPrice());
                techNetProfit = techNetProfit.add(netProfit);
                techCommission = techCommission.add(itemCommission);

                details.add(new MonthlyCommissionReportResponse.CommissionItemDetail(
                        item.getServiceOrder().getId(),
                        item.getId(),
                        item.getTag(),
                        item.getApplianceDisplayName(),
                        item.getDeliveredAt(),
                        item.getLaborCost(),
                        item.getTotalPrice(),
                        netProfit,
                        effectivePct,
                        itemCommission
                ));
            }

            totalCommissionableRevenue = totalCommissionableRevenue.add(techRevenue);
            totalCommissionAmount = totalCommissionAmount.add(techCommission);

            rows.add(new MonthlyCommissionReportResponse.TechnicianCommissionRow(
                    techId,
                    tech != null ? tech.name() : "Técnico removido",
                    tech != null ? tech.role() : null,
                    defaultPct,
                    details.size(),
                    techLabor.setScale(2, RoundingMode.HALF_UP),
                    techRevenue.setScale(2, RoundingMode.HALF_UP),
                    techNetProfit.setScale(2, RoundingMode.HALF_UP),
                    techCommission.setScale(2, RoundingMode.HALF_UP),
                    details
            ));
        }

        rows.sort(java.util.Comparator
                .comparing(MonthlyCommissionReportResponse.TechnicianCommissionRow::commissionTotal).reversed()
                .thenComparing(MonthlyCommissionReportResponse.TechnicianCommissionRow::technicianName));

        return new MonthlyCommissionReportResponse(
                start,
                end,
                zoneId.getId(),
                settings.technicianAssignmentEnabled(),
                basis,
                deliveredWithTechnicianCount,
                deliveredUnassignedCount,
                totalCommissionableRevenue.setScale(2, RoundingMode.HALF_UP),
                totalCommissionAmount.setScale(2, RoundingMode.HALF_UP),
                rows
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderItemQueueResponse> listBenchQueue(
            List<ItemStatus> statuses,
            String tag,
            int page,
            int size
    ) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        int safePage = Math.max(page, 0);
        int safeSize = clampPageSize(size);

        ParsedTag parsedTag = null;
        if (tag != null && !tag.isBlank()) {
            parsedTag = parseTag(tag.trim());
            if (parsedTag == null) {
                return PageResponse.empty(safePage, safeSize);
            }
        }

        final ParsedTag tagFilter = parsedTag;
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("itemIndex"))
        );

        Specification<ServiceOrderItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (tagFilter != null) {
                Join<ServiceOrderItem, ServiceOrder> orderJoin = root.join("serviceOrder");
                predicates.add(cb.equal(orderJoin.get("orderNumber"), tagFilter.orderNumber()));
                if (tagFilter.itemIndex() != null) {
                    predicates.add(cb.equal(root.get("itemIndex"), tagFilter.itemIndex()));
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<ServiceOrderItem> itemsPage = serviceOrderItemRepository.findAll(spec, pageable);
        if (itemsPage.isEmpty()) {
            return PageResponse.of(List.of(), itemsPage);
        }

        Set<UUID> customerIds = itemsPage.getContent().stream()
                .map(item -> item.getServiceOrder().getCustomerId())
                .collect(Collectors.toSet());
        Map<UUID, CustomerSnapshot> customersById = customerQuery.findAllByIds(customerIds, tenantId);
        Map<UUID, String> technicianNames = resolveTechnicianNames(tenantId, itemsPage.getContent());

        List<OrderItemQueueResponse> responses = itemsPage.getContent().stream()
                .map(item -> OrderItemQueueResponse.from(
                        item,
                        customersById.get(item.getServiceOrder().getCustomerId()),
                        item.getAssignedTechnicianId() != null
                                ? technicianNames.get(item.getAssignedTechnicianId())
                                : null
                ))
                .toList();

        return PageResponse.of(responses, itemsPage);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderItemQueueResponse> listAbandonmentEligibleItems(int page, int size) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        TenantSettingsSnapshot settings = tenantSettingsQuery.getSettings(tenantId);
        Instant cutoff = Instant.now().minus(Duration.ofDays(settings.abandonmentDays()));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampPageSize(size),
                Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("itemIndex"))
        );

        Specification<ServiceOrderItem> spec = (root, query, cb) -> {
            Predicate tenantMatch = cb.equal(root.get("tenantId"), tenantId);

            Predicate readyExpired = cb.and(
                    cb.equal(root.get("status"), ItemStatus.READY_FOR_PICKUP),
                    cb.lessThanOrEqualTo(
                            cb.coalesce(root.get("finishedAt"), root.get("updatedAt")),
                            cutoff
                    )
            );
            Predicate rejectedExpired = cb.and(
                    cb.equal(root.get("status"), ItemStatus.REJECTED),
                    cb.lessThanOrEqualTo(root.get("updatedAt"), cutoff)
            );

            return cb.and(tenantMatch, cb.or(readyExpired, rejectedExpired));
        };

        Page<ServiceOrderItem> itemsPage = serviceOrderItemRepository.findAll(spec, pageable);
        if (itemsPage.isEmpty()) {
            return PageResponse.of(List.of(), itemsPage);
        }

        Set<UUID> customerIds = itemsPage.getContent().stream()
                .map(item -> item.getServiceOrder().getCustomerId())
                .collect(Collectors.toSet());
        Map<UUID, CustomerSnapshot> customersById = customerQuery.findAllByIds(customerIds, tenantId);
        Map<UUID, String> technicianNames = resolveTechnicianNames(tenantId, itemsPage.getContent());

        List<OrderItemQueueResponse> responses = itemsPage.getContent().stream()
                .map(item -> OrderItemQueueResponse.from(
                        item,
                        customersById.get(item.getServiceOrder().getCustomerId()),
                        item.getAssignedTechnicianId() != null
                                ? technicianNames.get(item.getAssignedTechnicianId())
                                : null
                ))
                .toList();

        return PageResponse.of(responses, itemsPage);
    }

    private Map<UUID, ServiceOrderResponse.ParentItemRef> resolveParentItemRefs(
            UUID tenantId,
            List<ServiceOrderItem> items
    ) {
        Set<UUID> parentIds = items.stream()
                .map(ServiceOrderItem::getParentItemId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return serviceOrderItemRepository.findAllByTenantIdAndIdIn(tenantId, parentIds).stream()
                .collect(Collectors.toMap(
                        ServiceOrderItem::getId,
                        parent -> new ServiceOrderResponse.ParentItemRef(parent.getServiceOrder().getId(), parent.getTag())
                ));
    }

    private Map<UUID, String> resolveTechnicianNames(UUID tenantId, List<ServiceOrderItem> items) {
        Set<UUID> techIds = items.stream()
                .map(ServiceOrderItem::getAssignedTechnicianId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (techIds.isEmpty()) {
            return Map.of();
        }
        return userDirectoryQuery.findAllByIds(techIds, tenantId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name()));
    }

    private Map<UUID, List<QuoteOptionResponse>> resolveQuoteOptions(UUID tenantId, List<ServiceOrderItem> items) {
        List<UUID> itemIds = items.stream().map(ServiceOrderItem::getId).toList();
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return quoteOptionRepository
                .findAllByTenantIdAndServiceOrderItemIdInOrderByServiceOrderItemIdAscOptionIndexAsc(tenantId, itemIds)
                .stream()
                .collect(Collectors.groupingBy(
                        QuoteOption::getServiceOrderItemId,
                        Collectors.mapping(QuoteOptionResponse::from, Collectors.toList())
                ));
    }

    private static boolean isCurrentUserOwner() {
        return "OWNER".equals(SecurityUtils.requireCurrentUser().role());
    }

    private static int clampPageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static ParsedTag parseTag(String rawTag) {
        Matcher matcher = TAG_PATTERN.matcher(rawTag);
        if (!matcher.matches()) {
            return null;
        }
        try {
            long orderNumber = Long.parseLong(matcher.group(1));
            Integer itemIndex = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : null;
            return new ParsedTag(orderNumber, itemIndex);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ParsedTag(long orderNumber, Integer itemIndex) {
    }
}
