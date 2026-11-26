package com.benchos.api.order.internal.web;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.WhatsappMessageStage;
import com.benchos.api.order.internal.service.OrderItemLifecycleService;
import com.benchos.api.order.internal.service.ServiceOrderService;
import com.benchos.api.order.internal.web.dto.CreateServiceOrderRequest;
import com.benchos.api.order.internal.web.dto.MonthlyCommissionReportResponse;
import com.benchos.api.order.internal.web.dto.RepairProfitabilityResponse;
import com.benchos.api.order.internal.web.dto.ServiceOrderResponse;
import com.benchos.api.order.internal.web.dto.WhatsappLinkResponse;
import com.benchos.api.order.internal.web.dto.WorkshopAnalyticsResponse;
import com.benchos.api.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class ServiceOrderController {

    private final ServiceOrderService serviceOrderService;
    private final OrderItemLifecycleService lifecycleService;

    public ServiceOrderController(
            ServiceOrderService serviceOrderService,
            OrderItemLifecycleService lifecycleService
    ) {
        this.serviceOrderService = serviceOrderService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public ServiceOrderResponse createOrder(@Valid @RequestBody CreateServiceOrderRequest request) {
        return serviceOrderService.createOrder(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public PageResponse<ServiceOrderResponse> listOrders(
            @RequestParam(required = false) Long orderNumber,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return serviceOrderService.listOrders(orderNumber, customerId, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public ServiceOrderResponse getOrder(@PathVariable UUID id) {
        return serviceOrderService.getOrder(id);
    }

    @GetMapping("/by-token/{publicToken}")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public ServiceOrderResponse getOrderByPublicToken(@PathVariable UUID publicToken) {
        return serviceOrderService.getOrderByPublicToken(publicToken);
    }

    @GetMapping("/profitability")
    @PreAuthorize("hasRole('OWNER')")
    public RepairProfitabilityResponse getRepairProfitability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String zone
    ) {
        return serviceOrderService.getRepairProfitability(from, to, zone);
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('OWNER')")
    public WorkshopAnalyticsResponse getWorkshopAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String zone
    ) {
        return serviceOrderService.getWorkshopAnalytics(from, to, zone);
    }

    @GetMapping("/commissions")
    @PreAuthorize("hasRole('OWNER')")
    public MonthlyCommissionReportResponse getMonthlyCommissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String zone
    ) {
        return serviceOrderService.getMonthlyCommissionReport(from, to, zone);
    }

    @GetMapping("/{id}/whatsapp-link")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public WhatsappLinkResponse getOrderWhatsappLink(
            @PathVariable UUID id,
            @RequestParam(required = false) WhatsappMessageStage stage
    ) {
        return lifecycleService.generateOrderWhatsappLink(id, stage);
    }
}
