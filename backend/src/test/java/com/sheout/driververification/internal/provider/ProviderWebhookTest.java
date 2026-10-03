package com.sheout.driververification.internal.provider;

import com.sheout.sharedkernel.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The provider webhook is closed by default, and once open takes only a body signed with the shared secret. */
class ProviderWebhookTest {

    private static final byte[] BODY = "{\"anything\":\"the vendor sends\"}".getBytes(StandardCharsets.UTF_8);

    private final BackgroundCheckService checks = mock(BackgroundCheckService.class);

    @Test
    void theManualDefaultConfiguresNothing() {
        ManualVerificationProvider manual = new ManualVerificationProvider();
        assertThat(manual.configured()).isFalse();
        assertThat(manual.parseWebhook(BODY)).isEmpty();
        assertThat(manual.fetchResult("ref")).isEmpty();
    }

    @Test
    void closedWithoutAProvider() {
        when(checks.providerConfigured()).thenReturn(false);
        var controller = new VerificationProviderWebhookController(checks, "secret", "X-Provider-Signature");

        assertThatThrownBy(() -> controller.receive(BODY, Map.of("X-Provider-Signature", sign("secret"))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(checks, never()).acceptWebhook(any());
    }

    @Test
    void closedWithoutASecret() {
        when(checks.providerConfigured()).thenReturn(true);
        var controller = new VerificationProviderWebhookController(checks, "", "X-Provider-Signature");

        assertThatThrownBy(() -> controller.receive(BODY, Map.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aWrongOrMissingSignatureIsRefused() {
        when(checks.providerConfigured()).thenReturn(true);
        var controller = new VerificationProviderWebhookController(checks, "secret", "X-Provider-Signature");

        assertThatThrownBy(() -> controller.receive(BODY, Map.of("x-provider-signature", sign("not-the-secret"))))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> controller.receive(BODY, Map.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(checks, never()).acceptWebhook(any());
    }

    @Test
    void aSignedBodyIsHandedToTheProvider() {
        when(checks.providerConfigured()).thenReturn(true);
        when(checks.acceptWebhook(BODY)).thenReturn(1);
        var controller = new VerificationProviderWebhookController(checks, "secret", "X-Provider-Signature");

        var response = controller.receive(BODY, Map.of("x-provider-signature", sign("secret")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("matched", 1);
    }

    private static String sign(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(BODY));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
