package com.benchos.api.order.internal.web;

import com.benchos.api.order.PartRequestStatus;
import com.benchos.api.order.internal.service.PartRequestService;
import com.benchos.api.order.internal.web.dto.ArrivePartRequestRequest;
import com.benchos.api.order.internal.web.dto.CreatePartRequestRequest;
import com.benchos.api.order.internal.web.dto.MarkPartOrderedRequest;
import com.benchos.api.order.internal.web.dto.PartRequestResponse;
import com.benchos.api.order.internal.web.dto.PartRequestSummaryResponse;
import com.benchos.api.order.internal.web.dto.UpdatePartRequestRequest;
import com.benchos.api.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PartRequestController {

    private final PartRequestService partRequestService;

    public PartRequestController(PartRequestService partRequestService) {
        this.partRequestService = partRequestService;
    }

    @GetMapping("/orders/{orderId}/items/{itemId}/part-requests")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public List<PartRequestResponse> listForItem(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId
    ) {
        return partRequestService.listForItem(orderId, itemId);
    }

    @PostMapping("/orders/{orderId}/items/{itemId}/part-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public PartRequestResponse createForItem(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreatePartRequestRequest request
    ) {
        return partRequestService.createForItem(orderId, itemId, request);
    }

    @GetMapping("/part-requests")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public PageResponse<PartRequestResponse> listQueue(
            @RequestParam(required = false) List<PartRequestStatus> status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return partRequestService.listQueue(status, query, page, size);
    }

    @GetMapping("/part-requests/summary")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public PartRequestSummaryResponse getSummary() {
        return partRequestService.getSummary();
    }

    @PutMapping("/part-requests/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public PartRequestResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePartRequestRequest request
    ) {
        return partRequestService.update(id, request);
    }

    @PostMapping("/part-requests/{id}/order")
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public PartRequestResponse markOrdered(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) MarkPartOrderedRequest request
    ) {
        return partRequestService.markOrdered(id, request);
    }

    @PostMapping("/part-requests/{id}/reopen")
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public PartRequestResponse reopenToOrder(@PathVariable UUID id) {
        return partRequestService.reopenToOrder(id);
    }

    @PostMapping("/part-requests/{id}/arrive")
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public PartRequestResponse markArrived(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ArrivePartRequestRequest request
    ) {
        return partRequestService.markArrived(id, request);
    }

    @DeleteMapping("/part-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER', 'TECHNICIAN')")
    public void delete(@PathVariable UUID id) {
        partRequestService.delete(id);
    }
}
