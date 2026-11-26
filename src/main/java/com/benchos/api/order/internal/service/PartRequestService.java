package com.benchos.api.order.internal.service;

import com.benchos.api.customer.CustomerQuery;
import com.benchos.api.customer.CustomerSnapshot;
import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.PartRequestArrivedEvent;
import com.benchos.api.order.PartRequestStatus;
import com.benchos.api.order.PaymentMethod;
import com.benchos.api.order.internal.entity.PartCatalogItem;
import com.benchos.api.order.internal.entity.PartRequest;
import com.benchos.api.order.internal.entity.ServiceOrder;
import com.benchos.api.order.internal.entity.ServiceOrderItem;
import com.benchos.api.order.internal.repository.PartCatalogRepository;
import com.benchos.api.order.internal.repository.PartRequestRepository;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.order.internal.web.dto.ArrivePartRequestRequest;
import com.benchos.api.order.internal.web.dto.CreatePartRequestRequest;
import com.benchos.api.order.internal.web.dto.MarkPartOrderedRequest;
import com.benchos.api.order.internal.web.dto.PartRequestResponse;
import com.benchos.api.order.internal.web.dto.PartRequestSummaryResponse;
import com.benchos.api.order.internal.web.dto.UpdatePartRequestRequest;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.security.SecurityUtils;
import com.benchos.api.shared.tenant.TenantContext;
import com.benchos.api.shared.web.PageResponse;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PartRequestService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<PartRequestStatus> PENDING_STATUSES =
            EnumSet.of(PartRequestStatus.TO_ORDER, PartRequestStatus.ORDERED);
    private static final Pattern TAG_PATTERN = Pattern.compile("^#?(\\d+)(?:-(\\d+))?$");

    private final PartRequestRepository partRequestRepository;
    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final PartCatalogRepository partCatalogRepository;
    private final CustomerQuery customerQuery;
    private final ApplicationEventPublisher eventPublisher;

    public PartRequestService(
            PartRequestRepository partRequestRepository,
            ServiceOrderItemRepository serviceOrderItemRepository,
            PartCatalogRepository partCatalogRepository,
            CustomerQuery customerQuery,
            ApplicationEventPublisher eventPublisher
    ) {
        this.partRequestRepository = partRequestRepository;
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.partCatalogRepository = partCatalogRepository;
        this.customerQuery = customerQuery;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<PartRequestResponse> listForItem(UUID orderId, UUID itemId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        ServiceOrderItem item = serviceOrderItemRepository
                .findByIdAndServiceOrderIdAndTenantId(itemId, orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order item not found"));

        List<PartRequest> requests = partRequestRepository
                .findAllByTenantIdAndServiceOrderItemIdOrderByCreatedAtAsc(tenantId, item.getId());
        if (requests.isEmpty()) {
            return List.of();
        }
        String customerName = resolveCustomerName(item.getServiceOrder().getCustomerId(), tenantId);
        return requests.stream()
                .map(req -> PartRequestResponse.from(req, customerName))
                .toList();
    }

    @Transactional
    public PartRequestResponse createForItem(UUID orderId, UUID itemId, CreatePartRequestRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        UUID userId = SecurityUtils.requireCurrentUserId();

        ServiceOrderItem item = serviceOrderItemRepository
                .findForUpdateByIdAndServiceOrderIdAndTenantId(itemId, orderId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service order item not found"));

        if (item.getStatus().isTerminal()) {
            throw new BusinessRuleException("Cannot request parts for an item in terminal status (" + item.getStatus() + ")");
        }

        UUID catalogId = request.partCatalogId();
        String partName = request.partName();
        BigDecimal estimatedUnitCost = request.estimatedUnitCost();

        if (catalogId != null) {
            PartCatalogItem catalogItem = partCatalogRepository.findByIdAndTenantIdAndActiveTrue(catalogId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Part catalog item not found"));
            if (partName == null || partName.trim().isEmpty()) {
                partName = catalogItem.getName();
            }
            if (estimatedUnitCost == null) {
                estimatedUnitCost = catalogItem.getAverageCost();
            }
        }

        int quantity = request.quantity() != null ? request.quantity() : 1;
        Instant now = Instant.now();

        PartRequest partRequest = new PartRequest(
                tenantId,
                item,
                catalogId,
                partName,
                quantity,
                estimatedUnitCost,
                request.supplierName(),
                request.purchaseUrl(),
                request.notes(),
                userId,
                now
        );

        PartRequest saved = partRequestRepository.saveAndFlush(partRequest);

        if (Boolean.TRUE.equals(request.moveItemToWaitingParts())
                && (item.getStatus() == ItemStatus.APPROVED || item.getStatus() == ItemStatus.IN_REPAIR)) {
            item.transitionTo(ItemStatus.WAITING_PARTS, null, null, now);
            serviceOrderItemRepository.saveAndFlush(item);
        }

        String customerName = resolveCustomerName(item.getServiceOrder().getCustomerId(), tenantId);
        return PartRequestResponse.from(saved, customerName);
    }

    @Transactional(readOnly = true)
    public PageResponse<PartRequestResponse> listQueue(
            List<PartRequestStatus> statuses,
            String query,
            int page,
            int size
    ) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        String trimmedQuery = query != null ? query.trim() : null;

        Sort sort = (statuses != null && statuses.size() == 1 && statuses.contains(PartRequestStatus.ARRIVED))
                ? Sort.by(Sort.Order.desc("arrivedAt"), Sort.Order.desc("createdAt"))
                : Sort.by(Sort.Order.asc("createdAt"));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampPageSize(size),
                sort
        );

        Specification<PartRequest> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            } else {
                predicates.add(cb.notEqual(root.get("status"), PartRequestStatus.CANCELLED));
            }

            if (trimmedQuery != null && !trimmedQuery.isEmpty()) {
                Join<PartRequest, ServiceOrderItem> itemJoin = root.join("serviceOrderItem");
                Join<ServiceOrderItem, ServiceOrder> orderJoin = itemJoin.join("serviceOrder");

                String likePattern = "%" + trimmedQuery.toLowerCase(Locale.ROOT) + "%";
                List<Predicate> orPredicates = new ArrayList<>();
                orPredicates.add(cb.like(cb.lower(root.get("partName")), likePattern));
                orPredicates.add(cb.like(cb.lower(cb.coalesce(root.get("supplierName"), "")), likePattern));
                orPredicates.add(cb.like(cb.lower(itemJoin.get("applianceType")), likePattern));
                orPredicates.add(cb.like(cb.lower(itemJoin.get("brand")), likePattern));
                orPredicates.add(cb.like(cb.lower(cb.coalesce(itemJoin.get("model"), "")), likePattern));

                Matcher tagMatcher = TAG_PATTERN.matcher(trimmedQuery);
                if (tagMatcher.matches()) {
                    try {
                        long orderNum = Long.parseLong(tagMatcher.group(1));
                        if (tagMatcher.group(2) != null) {
                            int itemIdx = Integer.parseInt(tagMatcher.group(2));
                            orPredicates.add(cb.and(
                                    cb.equal(orderJoin.get("orderNumber"), orderNum),
                                    cb.equal(itemJoin.get("itemIndex"), itemIdx)
                            ));
                        } else {
                            orPredicates.add(cb.equal(orderJoin.get("orderNumber"), orderNum));
                        }
                    } catch (NumberFormatException ignored) {
                        // Ignore overflow numeric strings
                    }
                }

                predicates.add(cb.or(orPredicates.toArray(Predicate[]::new)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<PartRequest> requestPage = partRequestRepository.findAll(spec, pageable);
        if (requestPage.isEmpty()) {
            return PageResponse.of(List.of(), requestPage);
        }

        Set<UUID> customerIds = requestPage.getContent().stream()
                .map(pr -> pr.getServiceOrderItem().getServiceOrder().getCustomerId())
                .collect(Collectors.toSet());
        Map<UUID, CustomerSnapshot> customers = customerQuery.findAllByIds(customerIds, tenantId);

        List<PartRequestResponse> responses = requestPage.getContent().stream()
                .map(pr -> {
                    UUID customerId = pr.getServiceOrderItem().getServiceOrder().getCustomerId();
                    CustomerSnapshot customer = customers.get(customerId);
                    return PartRequestResponse.from(pr, customer != null ? customer.name() : null);
                })
                .toList();

        return PageResponse.of(responses, requestPage);
    }

    @Transactional(readOnly = true)
    public PartRequestSummaryResponse getSummary() {
        UUID tenantId = TenantContext.requireCurrentTenant();
        List<PartRequest> all = partRequestRepository.findAllByTenantId(tenantId);

        long toOrderCount = 0;
        long orderedCount = 0;
        long arrivedCount = 0;
        BigDecimal estimatedToOrderCost = ZERO;
        BigDecimal estimatedOrderedCost = ZERO;

        for (PartRequest pr : all) {
            switch (pr.getStatus()) {
                case TO_ORDER -> {
                    toOrderCount++;
                    estimatedToOrderCost = estimatedToOrderCost.add(pr.getEffectiveTotalCost());
                }
                case ORDERED -> {
                    orderedCount++;
                    estimatedOrderedCost = estimatedOrderedCost.add(pr.getEffectiveTotalCost());
                }
                case ARRIVED -> arrivedCount++;
                case CANCELLED -> {
                    // Not counted in active shopping metrics
                }
            }
        }

        return new PartRequestSummaryResponse(
                toOrderCount,
                orderedCount,
                arrivedCount,
                estimatedToOrderCost.setScale(2, RoundingMode.HALF_UP),
                estimatedOrderedCost.setScale(2, RoundingMode.HALF_UP)
        );
    }

    @Transactional
    public PartRequestResponse update(UUID id, UpdatePartRequestRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        PartRequest partRequest = partRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Part request not found"));

        int quantity = request.quantity() != null ? request.quantity() : partRequest.getQuantity();
        partRequest.updateDetails(
                request.partName(),
                quantity,
                request.estimatedUnitCost(),
                request.actualUnitCost(),
                request.supplierName(),
                request.purchaseUrl(),
                request.notes(),
                Instant.now()
        );

        PartRequest saved = partRequestRepository.saveAndFlush(partRequest);
        String customerName = resolveCustomerName(saved.getServiceOrderItem().getServiceOrder().getCustomerId(), tenantId);
        return PartRequestResponse.from(saved, customerName);
    }

    @Transactional
    public PartRequestResponse markOrdered(UUID id, MarkPartOrderedRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        PartRequest partRequest = partRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Part request not found"));

        partRequest.markOrdered(
                request != null ? request.supplierName() : null,
                request != null ? request.purchaseUrl() : null,
                request != null ? request.actualUnitCost() : null,
                request != null ? request.notes() : null,
                Instant.now()
        );

        PartRequest saved = partRequestRepository.saveAndFlush(partRequest);
        String customerName = resolveCustomerName(saved.getServiceOrderItem().getServiceOrder().getCustomerId(), tenantId);
        return PartRequestResponse.from(saved, customerName);
    }

    @Transactional
    public PartRequestResponse reopenToOrder(UUID id) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        PartRequest partRequest = partRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Part request not found"));

        partRequest.reopenToOrder(Instant.now());
        PartRequest saved = partRequestRepository.saveAndFlush(partRequest);
        String customerName = resolveCustomerName(saved.getServiceOrderItem().getServiceOrder().getCustomerId(), tenantId);
        return PartRequestResponse.from(saved, customerName);
    }

    @Transactional
    public PartRequestResponse markArrived(UUID id, ArrivePartRequestRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();

        boolean recordExpense = request != null && Boolean.TRUE.equals(request.recordExpense());
        if (recordExpense) {
            String role = SecurityUtils.requireCurrentUser().role();
            if (!"OWNER".equals(role)) {
                throw new AccessDeniedException("Only shop owners can record part purchase expenses in finance");
            }
        }

        PartRequest partRequest = partRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Part request not found"));

        ServiceOrderItem itemRef = partRequest.getServiceOrderItem();
        ServiceOrderItem lockedItem = serviceOrderItemRepository
                .findForUpdateByIdAndServiceOrderIdAndTenantId(
                        itemRef.getId(),
                        itemRef.getServiceOrder().getId(),
                        tenantId
                )
                .orElseThrow(() -> new ResourceNotFoundException("Service order item not found"));

        Instant now = Instant.now();
        BigDecimal actualUnitCost = request != null ? request.actualUnitCost() : null;
        boolean addToItemPartsCost = request == null || !Boolean.FALSE.equals(request.addToItemPartsCost());
        boolean moveItemToInRepair = request == null || !Boolean.FALSE.equals(request.moveItemToInRepair());

        partRequest.markArrived(actualUnitCost, recordExpense, now);
        PartRequest saved = partRequestRepository.saveAndFlush(partRequest);

        BigDecimal effectiveTotal = saved.getEffectiveTotalCost();
        if (addToItemPartsCost && effectiveTotal.compareTo(BigDecimal.ZERO) > 0) {
            lockedItem.addActualPartsCost(effectiveTotal, now);
        }

        if (moveItemToInRepair && lockedItem.getStatus() == ItemStatus.WAITING_PARTS) {
            long remainingPending = partRequestRepository.countByTenantIdAndServiceOrderItemIdAndStatusIn(
                    tenantId,
                    lockedItem.getId(),
                    PENDING_STATUSES
            );
            if (remainingPending == 0) {
                lockedItem.transitionTo(ItemStatus.IN_REPAIR, null, null, now);
            }
        }
        serviceOrderItemRepository.saveAndFlush(lockedItem);

        if (saved.isExpenseRecorded() && effectiveTotal.compareTo(BigDecimal.ZERO) > 0) {
            PaymentMethod paymentMethod = (request != null && request.paymentMethod() != null)
                    ? request.paymentMethod()
                    : PaymentMethod.PIX;
            eventPublisher.publishEvent(new PartRequestArrivedEvent(
                    tenantId,
                    saved.getId(),
                    lockedItem.getServiceOrder().getId(),
                    lockedItem.getId(),
                    lockedItem.getServiceOrder().getOrderNumber(),
                    lockedItem.getItemIndex(),
                    saved.getPartName(),
                    saved.getQuantity(),
                    effectiveTotal,
                    saved.getSupplierName(),
                    paymentMethod,
                    now
            ));
        }

        String customerName = resolveCustomerName(lockedItem.getServiceOrder().getCustomerId(), tenantId);
        return PartRequestResponse.from(saved, customerName);
    }

    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        PartRequest partRequest = partRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Part request not found"));

        if (!partRequest.getStatus().isPending()) {
            throw new BusinessRuleException("Arrived part requests cannot be deleted");
        }
        partRequestRepository.delete(partRequest);
    }

    private String resolveCustomerName(UUID customerId, UUID tenantId) {
        CustomerSnapshot snapshot = customerQuery.findAllByIds(List.of(customerId), tenantId).get(customerId);
        return snapshot != null ? snapshot.name() : null;
    }

    private static int clampPageSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
