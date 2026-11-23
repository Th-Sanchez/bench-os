package com.benchos.api.order.internal.web;

import com.benchos.api.order.internal.service.PublicOrderService;
import com.benchos.api.order.internal.web.dto.PublicOrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
