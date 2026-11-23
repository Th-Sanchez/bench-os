package com.benchos.api.shared.storage;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public S3ObjectStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUploadUrl generateUploadUrl(String storageKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlTtl())
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUploadUrl(
                storageKey,
                presigned.url().toString(),
                contentType,
                presigned.expiration()
        );
    }

    @Override
    public PresignedDownloadUrl generateDownloadUrl(String storageKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadUrlTtl())
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return new PresignedDownloadUrl(
                presigned.url().toString(),
                presigned.expiration()
        );
    }

    @Override
    public void deleteObject(String storageKey) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build();
        s3Client.deleteObject(deleteRequest);
    }
}
