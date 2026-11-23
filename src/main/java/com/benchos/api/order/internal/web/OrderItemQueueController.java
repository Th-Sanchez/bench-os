package com.benchos.api.order.internal.web;

import com.benchos.api.order.ItemStatus;
import com.benchos.api.order.internal.service.ServiceOrderService;
import com.benchos.api.order.internal.web.dto.OrderItemQueueResponse;
import com.benchos.api.shared.web.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/order-items")
public class OrderItemQueueController {

    private final ServiceOrderService serviceOrderService;

    public OrderItemQueueController(ServiceOrderService serviceOrderService) {
        this.serviceOrderService = serviceOrderService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public PageResponse<OrderItemQueueResponse> listBenchQueue(
            @RequestParam(required = false) List<ItemStatus> status,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return serviceOrderService.listBenchQueue(status, tag, page, size);
    }
}
