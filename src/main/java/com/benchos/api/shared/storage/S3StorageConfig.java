package com.benchos.api.shared.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();

        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(s3Config);

        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties properties) {
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();

        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(s3Config);

        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentialsProvider(StorageProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
        );
    }
}
