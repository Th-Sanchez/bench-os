package com.benchos.api.shared.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class S3ObjectStorageServiceTest {

    @Test
    void generatesRealSigV4PresignedUrlsAndDeletesObjectsViaS3Client() {
        StorageProperties properties = new StorageProperties(
                "https://r2.local.benchos.test",
                "auto",
                "benchos-media",
                "test-access-key",
                "test-secret-key",
                Duration.ofMinutes(10),
                Duration.ofHours(1),
                true
        );

        S3StorageConfig config = new S3StorageConfig();
        S3Client mockS3Client = mock(S3Client.class);

        try (S3Presigner presigner = config.s3Presigner(properties)) {
            S3ObjectStorageService storageService = new S3ObjectStorageService(mockS3Client, presigner, properties);

            String key = "tenants/t1/orders/o1/items/i1/photo.jpg";
            PresignedUploadUrl upload = storageService.generateUploadUrl(key, "image/jpeg");
            assertThat(upload.storageKey()).isEqualTo(key);
            assertThat(upload.contentType()).isEqualTo("image/jpeg");
            assertThat(upload.uploadUrl())
                    .startsWith("https://r2.local.benchos.test/benchos-media/" + key)
                    .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                    .contains("X-Amz-Signature=");
            assertThat(upload.expiresAt()).isAfter(Instant.now());

            PresignedDownloadUrl download = storageService.generateDownloadUrl(key);
            assertThat(download.downloadUrl())
                    .startsWith("https://r2.local.benchos.test/benchos-media/" + key)
                    .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                    .contains("X-Amz-Signature=");
            assertThat(download.expiresAt()).isAfter(Instant.now());

            storageService.deleteObject(key);
            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(mockS3Client).deleteObject(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo("benchos-media");
            assertThat(captor.getValue().key()).isEqualTo(key);
        }
    }
}
