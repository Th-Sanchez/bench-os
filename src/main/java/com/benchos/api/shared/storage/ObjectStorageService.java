package com.benchos.api.shared.storage;

/**
 * Port for S3-compatible object storage (Cloudflare R2) using Presigned URLs
 * so binary media payloads never pass through JVM memory.
 */
public interface ObjectStorageService {

    PresignedUploadUrl generateUploadUrl(String storageKey, String contentType);

    PresignedDownloadUrl generateDownloadUrl(String storageKey);

    void deleteObject(String storageKey);
}
