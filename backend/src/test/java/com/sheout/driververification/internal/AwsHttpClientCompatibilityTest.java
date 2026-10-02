package com.sheout.driververification.internal;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The AWS SDK and the HTTP client library under it agree.
 * <p>
 * Spring Boot pins Apache HttpClient 5 to a version older than the one the
 * AWS SDK's client is built against. With the older one, the first
 * Rekognition call threw NoClassDefFoundError (TlsSocketStrategy) - an
 * Error, which ServerFaceCheck did not catch - and every partner with a
 * verified selfie got a 500 at the start of her shift on the day the server
 * face check was switched on. Building the clients loads the HTTP client
 * classes, so a clash fails here, in CI, and not on a partner's phone. No
 * network and no real credentials: nothing is called.
 */
class AwsHttpClientCompatibilityTest {

    private static final StaticCredentialsProvider FAKE =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));

    @Test
    void rekognitionAndS3ClientsBuildWithTheHttpClientOnTheClasspath() {
        assertThatCode(() -> {
            try (RekognitionClient rekognition = RekognitionClient.builder().region(Region.AP_SOUTH_1).credentialsProvider(FAKE).build();
                 S3Client s3 = S3Client.builder().region(Region.AP_SOUTH_1).credentialsProvider(FAKE).build()) {
                assertThat(rekognition).isNotNull();
                assertThat(s3).isNotNull();
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void aFailureInsideTheFaceCheckIsNoOpinionNotAnError() {
        // An endpoint nobody listens on: whatever goes wrong, the shift goes on with the phone's result.
        System.setProperty("aws.endpointUrl", "http://127.0.0.1:9");
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
        try {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);
            when(values.increment(anyString())).thenReturn(1L);
            ServerFaceCheck check = new ServerFaceCheck(redis, true, "ap-south-1", 10);
            assertThat(check.similarity(new byte[] {1, 2, 3}, new byte[] {4, 5, 6})).isEmpty();
        } finally {
            System.clearProperty("aws.endpointUrl");
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretAccessKey");
        }
    }
}
