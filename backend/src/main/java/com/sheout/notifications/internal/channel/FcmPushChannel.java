package com.sheout.notifications.internal.channel;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.WebpushConfig;
import com.sheout.notifications.internal.NotificationChannelType;
import com.sheout.notifications.internal.NotificationError;
import com.sheout.notifications.internal.SendFailure;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Push through Firebase Cloud Messaging to the web apps.
 * <p>
 * DATA-ONLY MESSAGES, DELIBERATELY. FCM can build and show the notification
 * itself, but only through its own service worker, which would replace the
 * apps' PWA worker. Instead the payload is plain data and the apps' own
 * service worker (public/push-sw.js) shows it - one place decides the title,
 * the icon, the vibration and where a tap goes, on every platform.
 * <p>
 * An {@link OutboundMessage.Urgency#ALERT} message is sent with the Web Push
 * {@code Urgency: high} header and a 30-second time-to-live: an offer a
 * partner can only accept for 15 seconds is worse than useless if a phone
 * delivers it two minutes later, after it has gone to somebody else.
 * <p>
 * Credentials are FIREBASE_PROJECT_ID and FIREBASE_CREDENTIALS_JSON (a
 * service-account key). Blank by default, like Twilio and Razorpay: sends
 * then fail as NOT_CONFIGURED, recorded, and nothing else is affected.
 */
@Component
public class FcmPushChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(FcmPushChannel.class);

    /** Seconds a normal notification waits for a device that is offline. A day, then it is stale. */
    private static final int NORMAL_TTL_SECONDS = 86_400;
    private static final int ALERT_TTL_SECONDS = 30;

    /**
     * FCM's answers meaning this token will never work again. SENDER_ID_MISMATCH
     * is a token minted for a different Firebase project - after a project
     * change, every old token says this.
     */
    private static final Set<MessagingErrorCode> TOKEN_GONE = Set.of(
            MessagingErrorCode.UNREGISTERED, MessagingErrorCode.SENDER_ID_MISMATCH);

    private final FirebaseMessaging messaging;
    private final String notConfiguredReason;

    public FcmPushChannel(@Value("${sheout.firebase.project-id:}") String projectId,
                          @Value("${sheout.firebase.credentials-json:}") String credentialsJson) {
        FirebaseMessaging built = null;
        String reason = null;
        if (projectId.isBlank() || credentialsJson.isBlank()) {
            reason = "Firebase is not configured on this deployment (missing: "
                    + (projectId.isBlank() ? "FIREBASE_PROJECT_ID " : "")
                    + (credentialsJson.isBlank() ? "FIREBASE_CREDENTIALS_JSON" : "") + ")";
            log.warn("Push notifications disabled - {}", reason);
        } else {
            try {
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)));
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setProjectId(projectId)
                        .build();
                FirebaseApp app = FirebaseApp.getApps().stream()
                        .filter(existing -> existing.getName().equals("sheout"))
                        .findFirst()
                        .orElseGet(() -> FirebaseApp.initializeApp(options, "sheout"));
                built = FirebaseMessaging.getInstance(app);
                log.info("Push notifications enabled for Firebase project {}", projectId);
            } catch (IOException | IllegalArgumentException e) {
                // A malformed key must not stop the application starting -
                // SMS, the inbox and everything else still work without push.
                reason = "FIREBASE_CREDENTIALS_JSON could not be read as a service-account key";
                log.error("Push notifications disabled - {}: {}", reason, e.getMessage());
            }
        }
        this.messaging = built;
        this.notConfiguredReason = reason;
    }

    public boolean isConfigured() {
        return messaging != null;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public Result<Void, SendFailure> send(String deviceToken, OutboundMessage message) {
        if (messaging == null) {
            return Result.failure(SendFailure.of(NotificationError.NOT_CONFIGURED, notConfiguredReason));
        }
        boolean alert = message.urgency() == OutboundMessage.Urgency.ALERT;
        Message.Builder fcm = Message.builder()
                .setToken(deviceToken)
                .putData("title", message.title())
                .putData("urgency", message.urgency().name())
                .setWebpushConfig(WebpushConfig.builder()
                        .putHeader("Urgency", alert ? "high" : "normal")
                        .putHeader("TTL", String.valueOf(alert ? ALERT_TTL_SECONDS : NORMAL_TTL_SECONDS))
                        .build());
        if (message.body() != null) {
            fcm.putData("body", message.body());
        }
        if (message.link() != null) {
            fcm.putData("link", message.link());
        }
        if (message.tag() != null) {
            fcm.putData("tag", message.tag());
        }
        try {
            messaging.send(fcm.build());
            return Result.success(null);
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            String detail = "FCM " + (code == null ? "error" : code.name()) + ": " + e.getMessage();
            // The token itself is never logged: it is a credential for
            // putting text on somebody's lock screen.
            if (code != null && TOKEN_GONE.contains(code)) {
                return Result.failure(SendFailure.of(NotificationError.RECIPIENT_GONE, detail));
            }
            log.error("Push send failed - {}", detail);
            return Result.failure(SendFailure.of(NotificationError.PROVIDER_ERROR, detail));
        } catch (RuntimeException e) {
            // A malformed token makes the SDK throw IllegalArgumentException
            // before any request; it is as dead as an unregistered one.
            if (e instanceof IllegalArgumentException) {
                return Result.failure(SendFailure.of(NotificationError.RECIPIENT_GONE, "Invalid device token"));
            }
            log.error("Push send failed - {}", e.getMessage());
            return Result.failure(SendFailure.of(NotificationError.PROVIDER_ERROR, "Could not reach FCM: " + e.getMessage()));
        }
    }
}
