package com.sheout.notifications.internal;

import com.sheout.sharedkernel.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Exists as its own bean, separate from NotificationEventListeners, purely
 * so its write can be REQUIRES_NEW - and REQUIRES_NEW only actually takes
 * effect when reached through the Spring proxy, i.e. only when a DIFFERENT
 * bean calls this injected NotificationLogService, never via {@code this.}
 * from inside the listener itself.
 * <p>
 * This matters here for the exact reason it mattered for payments'
 * BookingCompletedListener bug: NotificationEventListeners reacts via
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, which runs
 * while Spring still considers transaction synchronization "active" on the
 * thread even though the triggering transaction (e.g. BookingService.
 * completeTrip's) already committed. A save reached any other way - no
 * annotation, a plain @Transactional, or REQUIRES_NEW self-invoked via
 * {@code this.} - silently joins that stale synchronization instead of
 * opening a real one: it would return normally with a generated id, but
 * nothing would actually be committed (confirmed against Postgres for the
 * exact same shape of bug in PaymentService.createPendingPayment). See that
 * class's Javadoc for the full account of how this was found.
 */
@Service
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationLogService(NotificationLogRepository notificationLogRepository) {
        this.notificationLogRepository = notificationLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLog(UUID recipientAccountId, String recipientAddress, NotificationType type,
                           NotificationChannelType channel, Result<Void, NotificationError> outcome) {
        NotificationStatus status = outcome.isSuccess() ? NotificationStatus.SENT : NotificationStatus.FAILED;
        String failureReason = outcome.isFailure() ? outcome.error().toString() : null;
        notificationLogRepository.save(
                new NotificationLogEntity(recipientAccountId, recipientAddress, type, channel, status, failureReason));
    }
}
