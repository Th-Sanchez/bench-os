package com.benchos.api.order.internal.service;

import com.benchos.api.customer.CustomerQuery;
import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.iam.TenantSettingsQuery;
import com.benchos.api.iam.TenantSettingsSnapshot;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.order.internal.repository.ServiceOrderRepository;
import com.benchos.api.order.internal.web.dto.CreateServiceOrderItemRequest;
import com.benchos.api.order.internal.web.dto.CreateServiceOrderRequest;
import com.benchos.api.order.internal.web.dto.CreateWarrantyReturnRequest;
import com.benchos.api.order.internal.web.dto.OrderItemQueueResponse;
import com.benchos.api.order.internal.web.dto.RepairProfitabilityResponse;
import com.benchos.api.order.internal.web.dto.ServiceOrderResponse;
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
    private final TenantSequenceService tenantSequenceService;
    private final CustomerQuery customerQuery;
    private final TenantSettingsQuery tenantSettingsQuery;

    public ServiceOrderService(
            ServiceOrderRepository serviceOrderRepository,
            ServiceOrderItemRepository serviceOrderItemRepository,
            TenantSequenceService tenantSequenceService,
            CustomerQuery customerQuery,
            TenantSettingsQuery tenantSettingsQuery
    ) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.tenantSequenceService = tenantSequenceService;
        this.customerQuery = customerQuery;
        this.tenantSettingsQuery = tenantSettingsQuery;
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
                    itemReq.reportedIssue()
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

        Map<UUID, CustomerSnapshot> customersById = customerQuery.findAllByIds(customerIds, tenantId);

        List<ServiceOrderResponse> responses = ordersPage.getContent().stream()
                .map(order -> ServiceOrderResponse.from(
                        order,
                        customersById.get(order.getCustomerId()),
                        itemsByOrderId.getOrDefault(order.getId(), List.of()),
                        parentRefs
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

        return ServiceOrderResponse.from(order, customer, order.getItems(), parentRefs);
    }

    @Transactional(readOnly = true)
    public ServiceOrderResponse getOrderByPublicToken(UUID publicToken) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrder order = serviceOrderRepository.findWithItemsByPublicTokenAndTenantId(publicToken, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order not found"));

        CustomerSnapshot customer = customerQuery.findAllByIds(List.of(order.getCustomerId()), tenantId)
                .get(order.getCustomerId());
        Map<UUID, ServiceOrderResponse.ParentItemRef> parentRefs = resolveParentItemRefs(tenantId, order.getItems());

        return ServiceOrderResponse.from(order, customer, order.getItems(), parentRefs);
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

        List<OrderItemQueueResponse> responses = itemsPage.getContent().stream()
                .map(item -> OrderItemQueueResponse.from(
                        item,
                        customersById.get(item.getServiceOrder().getCustomerId())
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

        List<OrderItemQueueResponse> responses = itemsPage.getContent().stream()
                .map(item -> OrderItemQueueResponse.from(
                        item,
                        customersById.get(item.getServiceOrder().getCustomerId())
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
