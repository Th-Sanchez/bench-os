package com.benchos.api.shared.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(name = "benchos.storage.auto-create-bucket", havingValue = "true")
public class S3BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(S3BucketInitializer.class);

    private final S3Client s3Client;
    private final StorageProperties properties;

    public S3BucketInitializer(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = properties.bucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 storage bucket '{}' is ready", bucket);
        } catch (NoSuchBucketException ex) {
            createBucket(bucket);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                createBucket(bucket);
            } else {
                log.warn("Could not verify S3 bucket '{}' (status {}): {}", bucket, ex.statusCode(), ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Local S3 storage check skipped for bucket '{}': {}", bucket, ex.getMessage());
        }
    }

    private void createBucket(String bucket) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created S3 storage bucket '{}'", bucket);
        } catch (Exception ex) {
            log.warn("Failed to auto-create S3 bucket '{}': {}", bucket, ex.getMessage());
        }
    }
}
