package com.benchos.api.order.internal.web;

import com.benchos.api.order.internal.service.ItemPhotoService;
import com.benchos.api.order.internal.web.dto.ItemPhotoResponse;
import com.benchos.api.order.internal.web.dto.PresignPhotoUploadRequest;
import com.benchos.api.order.internal.web.dto.PresignPhotoUploadResponse;
import com.benchos.api.order.internal.web.dto.RegisterItemPhotoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/items/{itemId}/photos")
public class ItemPhotoController {

    private final ItemPhotoService itemPhotoService;

    public ItemPhotoController(ItemPhotoService itemPhotoService) {
        this.itemPhotoService = itemPhotoService;
    }

    @PostMapping("/presign")
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public PresignPhotoUploadResponse presignUpload(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody PresignPhotoUploadRequest request
    ) {
        return itemPhotoService.presignUpload(orderId, itemId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public ItemPhotoResponse registerPhoto(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody RegisterItemPhotoRequest request
    ) {
        return itemPhotoService.registerPhoto(orderId, itemId, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public List<ItemPhotoResponse> listPhotos(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId
    ) {
        return itemPhotoService.listPhotos(orderId, itemId);
    }

    @DeleteMapping("/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER', 'ATTENDANT', 'TECHNICIAN')")
    public void deletePhoto(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @PathVariable UUID photoId
    ) {
        itemPhotoService.deletePhoto(orderId, itemId, photoId);
    }
}
