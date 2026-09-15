package com.sheout.notifications.internal.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.notifications.internal.PushDeviceService;
import com.sheout.notifications.internal.channel.FcmPushChannel;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * Turning push on and off for a device, and the public configuration a
 * browser needs to ask Firebase for a token.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class PushDeviceController {

    private static final Logger log = LoggerFactory.getLogger(PushDeviceController.class);

    private final PushDeviceService devices;
    private final RateLimiter rateLimiter;
    private final PushConfig pushConfig;

    public PushDeviceController(PushDeviceService devices, RateLimiter rateLimiter, FcmPushChannel pushChannel,
                                ObjectMapper objectMapper,
                                @Value("${sheout.firebase.web-config-json:}") String webConfigJson,
                                @Value("${sheout.firebase.vapid-key:}") String vapidKey) {
        this.devices = devices;
        this.rateLimiter = rateLimiter;
        this.pushConfig = buildConfig(pushChannel.isConfigured(), objectMapper, webConfigJson, vapidKey);
    }

    /**
     * What the apps and the console initialise Firebase with. Every value
     * here is public by design - Firebase's web config and the VAPID public
     * key ship inside any web app that uses them - so this needs no login,
     * and it lets one backend setting configure all three front ends.
     * <p>
     * {@code enabled} is false unless the server can actually send: a device
     * that registers when nothing will ever be pushed to it would ask a
     * person for a permission that does nothing.
     */
    @GetMapping("/push-config")
    public ResponseEntity<PushConfig> pushConfig() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(pushConfig);
    }

    /** Idempotent; the apps call it on every start with whatever token Firebase currently holds. */
    @PostMapping("/devices")
    public ResponseEntity<Void> register(@Valid @RequestBody DeviceRequest request, HttpServletRequest http) {
        CurrentAccount caller = requireCaller();
        rateLimiter.tryConsume("push-register:" + caller.accountId(), 30, Duration.ofHours(1))
                .orThrow("Too many device registrations. Please try again later.");
        devices.register(caller.accountId(), caller.role(), request.token().trim(), http.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.noContent().build();
    }

    /** Signing out on this device. The same 204 whether or not it was registered to the caller. */
    @PostMapping("/devices/unregister")
    public ResponseEntity<Void> unregister(@Valid @RequestBody DeviceRequest request) {
        CurrentAccount caller = requireCaller();
        devices.unregister(caller.accountId(), request.token().trim());
        return ResponseEntity.noContent().build();
    }

    private static CurrentAccount requireCaller() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    private static PushConfig buildConfig(boolean serverCanSend, ObjectMapper objectMapper, String webConfigJson,
                                          String vapidKey) {
        if (!serverCanSend || webConfigJson.isBlank() || vapidKey.isBlank()) {
            return PushConfig.DISABLED;
        }
        try {
            Map<String, String> firebase = objectMapper.readValue(webConfigJson, new TypeReference<>() {});
            return new PushConfig(true, firebase, vapidKey.trim());
        } catch (Exception e) {
            log.error("Push disabled for the apps - FIREBASE_WEB_CONFIG_JSON is not a JSON object of strings: {}", e.getMessage());
            return PushConfig.DISABLED;
        }
    }

    public record DeviceRequest(@NotBlank @Size(max = 512) String token) {
    }

    public record PushConfig(boolean enabled, Map<String, String> firebase, String vapidKey) {
        static final PushConfig DISABLED = new PushConfig(false, null, null);
    }
}
