package com.benchos.api.order.internal.web;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.service.ServiceOrderService;
import com.benchos.api.order.internal.web.dto.CreateServiceOrderRequest;
import com.benchos.api.order.internal.web.dto.ServiceOrderResponse;
import com.benchos.api.shared.web.PageResponse;
import jakarta.validation.Valid;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class ServiceOrderController {

    private final ServiceOrderService serviceOrderService;

    public ServiceOrderController(ServiceOrderService serviceOrderService) {
        this.serviceOrderService = serviceOrderService;
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
}
