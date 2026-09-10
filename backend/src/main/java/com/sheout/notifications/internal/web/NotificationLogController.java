package com.sheout.notifications.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.notifications.internal.NotificationChannelType;
import com.sheout.notifications.internal.NotificationLogEntity;
import com.sheout.notifications.internal.NotificationLogRepository;
import com.sheout.notifications.internal.NotificationStatus;
import com.sheout.notifications.internal.NotificationType;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The caller's own notification history - what this module already records
 * for every alert it sends (see NotificationLogService), now readable by
 * the person it was sent to. Backs the bell icon in both apps, which until
 * now was a placeholder claiming no notifications module existed.
 * <p>
 * Self-service over HTTP with no cross-module Java caller, so it stays
 * internal rather than going on a public interface - same reasoning
 * SosController's trigger endpoint uses. Scoped to the caller by
 * construction: the account id comes from the token, never from the
 * request, so there is no id to authorize and no way to read someone
 * else's history.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationLogController {

    private static final int MAX_LIMIT = 100;

    private final NotificationLogRepository repository;

    public NotificationLogController(NotificationLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    public ResponseEntity<List<NotificationView>> myNotifications(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        int capped = Math.clamp(limit, 1, MAX_LIMIT);
        List<NotificationView> items = repository
                .findByRecipientAccountIdOrderByCreatedAtDesc(caller.accountId(), PageRequest.of(0, capped))
                .stream()
                .map(NotificationView::from)
                .toList();
        return ResponseEntity.ok(items);
    }

    /**
     * recipientAddress (the phone number an SMS went to) is deliberately
     * omitted - the caller already knows their own number, and an SOS log
     * row's address is an emergency contact's number, which does not belong
     * in a notification feed.
     */
    public record NotificationView(
            UUID id,
            NotificationType type,
            NotificationChannelType channel,
            NotificationStatus status,
            String failureReason,
            Instant createdAt
    ) {
        static NotificationView from(NotificationLogEntity e) {
            return new NotificationView(
                    e.getId(), e.getType(), e.getChannel(), e.getStatus(), e.getFailureReason(), e.getCreatedAt());
        }
    }
}
