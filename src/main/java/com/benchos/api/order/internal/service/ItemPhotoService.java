package com.benchos.api.order.internal.service;

import com.benchos.api.order.internal.entity.ItemPhoto;
import com.benchos.api.order.internal.repository.ItemPhotoRepository;
import com.benchos.api.order.internal.repository.ServiceOrderItemRepository;
import com.benchos.api.order.internal.web.dto.ItemPhotoResponse;
import com.benchos.api.order.internal.web.dto.PresignPhotoUploadRequest;
import com.benchos.api.order.internal.web.dto.PresignPhotoUploadResponse;
import com.benchos.api.order.internal.web.dto.RegisterItemPhotoRequest;
import com.benchos.api.shared.exception.BusinessRuleException;
import com.benchos.api.shared.exception.ConflictException;
import com.benchos.api.shared.exception.ResourceNotFoundException;
import com.benchos.api.shared.storage.ObjectStorageService;
import com.benchos.api.shared.storage.PresignedDownloadUrl;
import com.benchos.api.shared.storage.PresignedUploadUrl;
import com.benchos.api.shared.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ItemPhotoService {

    private final ServiceOrderItemRepository serviceOrderItemRepository;
    private final ItemPhotoRepository itemPhotoRepository;
    private final ObjectStorageService objectStorageService;

    public ItemPhotoService(
            ServiceOrderItemRepository serviceOrderItemRepository,
            ItemPhotoRepository itemPhotoRepository,
            ObjectStorageService objectStorageService
    ) {
        this.serviceOrderItemRepository = serviceOrderItemRepository;
        this.itemPhotoRepository = itemPhotoRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional(readOnly = true)
    public PresignPhotoUploadResponse presignUpload(UUID orderId, UUID itemId, PresignPhotoUploadRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        requireItemExists(orderId, itemId, tenantId);

        String normalizedContentType = ItemPhoto.validateAndNormalizeContentType(request.contentType());
        ensurePhotoQuotaAvailable(tenantId, itemId);

        String storageKey = ItemPhoto.generateStorageKey(tenantId, orderId, itemId, normalizedContentType);
        PresignedUploadUrl presigned = objectStorageService.generateUploadUrl(storageKey, normalizedContentType);

        return new PresignPhotoUploadResponse(
                presigned.storageKey(),
                presigned.uploadUrl(),
                presigned.contentType(),
                request.stage(),
                presigned.expiresAt()
        );
    }

    @Transactional
    public ItemPhotoResponse registerPhoto(UUID orderId, UUID itemId, RegisterItemPhotoRequest request) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        requireItemExists(orderId, itemId, tenantId);

        String normalizedContentType = ItemPhoto.validateAndNormalizeContentType(request.contentType());
        String storageKey = request.storageKey().trim();
        ItemPhoto.validateStorageKeyForItem(tenantId, orderId, itemId, storageKey, normalizedContentType);

        if (itemPhotoRepository.existsByStorageKey(storageKey)) {
            throw new ConflictException("Photo with this storage key is already registered");
        }
        ensurePhotoQuotaAvailable(tenantId, itemId);

        ItemPhoto saved = itemPhotoRepository.save(
                new ItemPhoto(tenantId, itemId, storageKey, normalizedContentType, request.stage())
        );

        PresignedDownloadUrl viewUrl = objectStorageService.generateDownloadUrl(saved.getStorageKey());
        return ItemPhotoResponse.from(saved, viewUrl);
    }

    @Transactional(readOnly = true)
    public List<ItemPhotoResponse> listPhotos(UUID orderId, UUID itemId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        requireItemExists(orderId, itemId, tenantId);

        return itemPhotoRepository.findAllByTenantIdAndServiceOrderItemIdOrderByCreatedAtAsc(tenantId, itemId)
                .stream()
                .map(photo -> ItemPhotoResponse.from(
                        photo,
                        objectStorageService.generateDownloadUrl(photo.getStorageKey())
                ))
                .toList();
    }

    @Transactional
    public void deletePhoto(UUID orderId, UUID itemId, UUID photoId) {
        UUID tenantId = TenantContext.requireCurrentTenant();
        requireItemExists(orderId, itemId, tenantId);

        ItemPhoto photo = itemPhotoRepository.findByIdAndServiceOrderItemIdAndTenantId(photoId, itemId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item photo not found"));

        itemPhotoRepository.delete(photo);
        objectStorageService.deleteObject(photo.getStorageKey());
    }

    private void requireItemExists(UUID orderId, UUID itemId, UUID tenantId) {
        if (serviceOrderItemRepository.findByIdAndServiceOrderIdAndTenantId(itemId, orderId, tenantId).isEmpty()) {
            throw new ResourceNotFoundException("Service order item not found");
        }
    }

    private void ensurePhotoQuotaAvailable(UUID tenantId, UUID itemId) {
        long count = itemPhotoRepository.countByTenantIdAndServiceOrderItemId(tenantId, itemId);
        if (count >= ItemPhoto.MAX_PHOTOS_PER_ITEM) {
            throw new BusinessRuleException(
                    "Maximum number of photos per item (" + ItemPhoto.MAX_PHOTOS_PER_ITEM + ") reached"
            );
        }
    }
}
