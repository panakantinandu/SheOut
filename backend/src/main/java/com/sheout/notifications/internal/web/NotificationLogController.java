package com.sheout.notifications.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.notifications.internal.NotificationInboxService;
import com.sheout.notifications.internal.NotificationLogEntity;
import com.sheout.notifications.internal.NotificationType;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * The caller's notification inbox - every notification SheOut sent the
 * account, with what it said, newest first.
 * <p>
 * Scoped to the caller by construction: the account comes from the token and
 * is part of every query, so another person's notification id is simply not
 * found - the same 404 as an id that never existed.
 * <p>
 * How a notification was delivered is deliberately absent. That is the
 * audit trail's business (notification_deliveries), and its contents - a
 * provider's error text, a device token, an emergency contact's number - are
 * not for this screen.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationLogController {

    private final NotificationInboxService inbox;

    public NotificationLogController(NotificationInboxService inbox) {
        this.inbox = inbox;
    }

    @GetMapping("/me")
    public ResponseEntity<InboxPage> myNotifications(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer pageSize) {
        CurrentAccount caller = requireCaller();
        PageResponse<NotificationView> items = PageResponse.from(
                inbox.page(caller.accountId(),
                        PageRequest.of(PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize))),
                NotificationView::from);
        return ResponseEntity.ok(new InboxPage(items, inbox.unreadCount(caller.accountId())));
    }

    /** For the bell's badge, without fetching a page. */
    @GetMapping("/me/unread-count")
    public ResponseEntity<UnreadCount> unreadCount() {
        CurrentAccount caller = requireCaller();
        return ResponseEntity.ok(new UnreadCount(inbox.unreadCount(caller.accountId())));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationView> markRead(@PathVariable UUID notificationId) {
        CurrentAccount caller = requireCaller();
        return inbox.markRead(caller.accountId(), notificationId)
                .map(notification -> ResponseEntity.ok(NotificationView.from(notification)))
                .orElseThrow(() -> ApiException.notFound("No such notification"));
    }

    @PostMapping("/me/read-all")
    public ResponseEntity<UnreadCount> markAllRead() {
        CurrentAccount caller = requireCaller();
        inbox.markAllRead(caller.accountId());
        return ResponseEntity.ok(new UnreadCount(0));
    }

    private static CurrentAccount requireCaller() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    public record InboxPage(PageResponse<NotificationView> page, long unreadCount) {
    }

    public record UnreadCount(long unreadCount) {
    }

    /** link is a path inside the app, never a full URL - see OutboundMessage. */
    public record NotificationView(UUID id, NotificationType type, String title, String body, String link,
                                   boolean read, Instant createdAt) {
        static NotificationView from(NotificationLogEntity e) {
            return new NotificationView(e.getId(), e.getType(), e.getTitle(), e.getBody(), e.getLink(),
                    e.getReadAt() != null, e.getCreatedAt());
        }
    }
}
