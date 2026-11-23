package com.benchos.api.order.internal.web;

import com.benchos.api.order.internal.service.PublicOrderService;
import com.benchos.api.order.internal.web.dto.PublicOrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/orders")
public class PublicOrderController {

    private final PublicOrderService publicOrderService;

    public PublicOrderController(PublicOrderService publicOrderService) {
        this.publicOrderService = publicOrderService;
    }

    @GetMapping("/{publicToken}")
    public PublicOrderResponse getPublicOrder(@PathVariable UUID publicToken) {
        return publicOrderService.getPublicOrder(publicToken);
    }

    @PostMapping("/{publicToken}/items/{itemIndex}/approve")
    public PublicOrderResponse approveItem(
            @PathVariable UUID publicToken,
            @PathVariable int itemIndex
    ) {
        return publicOrderService.approveItem(publicToken, itemIndex);
    }

    @PostMapping("/{publicToken}/items/{itemIndex}/reject")
    public PublicOrderResponse rejectItem(
            @PathVariable UUID publicToken,
            @PathVariable int itemIndex
    ) {
        return publicOrderService.rejectItem(publicToken, itemIndex);
    }
}
