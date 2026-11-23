package com.benchos.api.order.internal.web;

import com.benchos.api.order.internal.service.OrderItemLifecycleService;
import com.benchos.api.order.internal.web.dto.DeliverItemRequest;
import com.benchos.api.order.internal.web.dto.ServiceOrderItemResponse;
import com.benchos.api.order.internal.web.dto.TransitionItemStatusRequest;
import com.benchos.api.order.internal.web.dto.UpdateItemQuoteRequest;
import com.benchos.api.order.internal.web.dto.WhatsappLinkResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/items/{itemId}")
public class OrderItemLifecycleController {

    private final OrderItemLifecycleService lifecycleService;

    public OrderItemLifecycleController(OrderItemLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @PatchMapping("/quote")
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public ServiceOrderItemResponse updateQuote(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemQuoteRequest request
    ) {
        return lifecycleService.updateQuote(orderId, itemId, request);
    }

    @PostMapping("/status")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public ServiceOrderItemResponse transitionStatus(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody TransitionItemStatusRequest request
    ) {
        return lifecycleService.transitionStatus(orderId, itemId, request);
    }

    @PostMapping("/deliver")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public ServiceOrderItemResponse deliverItem(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody(required = false) DeliverItemRequest request
    ) {
        return lifecycleService.deliverItem(orderId, itemId, request);
    }

    @GetMapping("/whatsapp-link")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public WhatsappLinkResponse getWhatsappLink(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId
    ) {
        return lifecycleService.generateWhatsappLink(orderId, itemId);
    }
}
