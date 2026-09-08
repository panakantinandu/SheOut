package com.sheout.driververification.internal.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * S3-compatible object storage. Credentials come from the standard AWS SDK
 * env vars (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_REGION) via the
 * default credentials provider chain, not a SheOut-specific env var - no
 * need to reinvent what the SDK already reads. Bucket and an optional
 * custom endpoint (for a non-AWS S3-compatible provider) are SheOut config.
 * Active only when SHEOUT_DOCUMENT_STORAGE_PROVIDER=s3.
 */
@Component
@ConditionalOnProperty(name = "sheout.document-storage.provider", havingValue = "s3")
public class S3DocumentStorage implements DocumentStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration presignExpiry;

    public S3DocumentStorage(
            @Value("${sheout.document-storage.s3.bucket}") String bucket,
            @Value("${sheout.document-storage.s3.region:ap-south-1}") String region,
            @Value("${sheout.document-storage.s3.endpoint:}") String endpoint,
            @Value("${sheout.document-storage.s3.presign-expiry-minutes:15}") long presignExpiryMinutes
    ) {
        this.bucket = bucket;
        this.presignExpiry = Duration.ofMinutes(presignExpiryMinutes);

        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(!endpoint.isBlank())
                .build();

        var clientBuilder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .serviceConfiguration(serviceConfig);
        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .serviceConfiguration(serviceConfig);

        if (!endpoint.isBlank()) {
            clientBuilder.endpointOverride(URI.create(endpoint));
            presignerBuilder.endpointOverride(URI.create(endpoint));
        }

        this.s3Client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public String store(UUID accountId, String documentType, DocumentUpload upload) {
        String key = DocumentKeys.build(accountId, documentType, upload.filename());
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(upload.contentType())
                        .build(),
                RequestBody.fromBytes(upload.content())
        );
        return key;
    }

    @Override
    public String resolveUrl(String storageKey) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(presignExpiry)
                .getObjectRequest(getRequest)
                .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }
}
