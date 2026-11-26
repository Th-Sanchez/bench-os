package com.benchos.api.order.internal.web;

import com.benchos.api.order.internal.service.PublicOrderService;
import com.benchos.api.order.internal.web.dto.ApprovePublicQuoteRequest;
import com.benchos.api.order.internal.web.dto.PublicOrderResponse;
import com.benchos.api.order.internal.web.dto.SubmitOrderRatingRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/orders")
public class PublicOrderController {

    private static final String ROBOTS_HEADER = "X-Robots-Tag";
    private static final String ROBOTS_VALUE = "noindex, nofollow, noarchive";

    private final PublicOrderService publicOrderService;

    public PublicOrderController(PublicOrderService publicOrderService) {
        this.publicOrderService = publicOrderService;
    }

    @GetMapping("/{publicToken}")
    public ResponseEntity<PublicOrderResponse> getPublicOrder(@PathVariable UUID publicToken) {
        return okPrivate(publicOrderService.getPublicOrder(publicToken));
    }

    @PostMapping("/{publicToken}/items/{itemIndex}/approve")
    public ResponseEntity<PublicOrderResponse> approveItem(
            @PathVariable UUID publicToken,
            @PathVariable int itemIndex,
            @RequestBody(required = false) ApprovePublicQuoteRequest request
    ) {
        UUID quoteOptionId = request != null ? request.quoteOptionId() : null;
        return okPrivate(publicOrderService.approveItem(publicToken, itemIndex, quoteOptionId));
    }

    @PostMapping("/{publicToken}/items/{itemIndex}/reject")
    public ResponseEntity<PublicOrderResponse> rejectItem(
            @PathVariable UUID publicToken,
            @PathVariable int itemIndex
    ) {
        return okPrivate(publicOrderService.rejectItem(publicToken, itemIndex));
    }

    @PostMapping("/{publicToken}/rating")
    public ResponseEntity<PublicOrderResponse> submitRating(
            @PathVariable UUID publicToken,
            @Valid @RequestBody SubmitOrderRatingRequest request
    ) {
        return okPrivate(publicOrderService.submitRating(publicToken, request));
    }

    private static ResponseEntity<PublicOrderResponse> okPrivate(PublicOrderResponse body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(ROBOTS_HEADER, ROBOTS_VALUE)
                .body(body);
    }
}
