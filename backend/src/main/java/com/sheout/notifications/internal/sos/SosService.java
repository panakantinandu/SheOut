package com.sheout.notifications.internal.sos;




import java.util.Map;import com.sheout.users.AppLanguage;import com.sheout.notifications.internal.NotificationCopy;import com.sheout.notifications.SosAlertRaised;
import com.sheout.notifications.internal.NotificationChannelType;
import com.sheout.notifications.internal.NotificationLogService;
import com.sheout.notifications.internal.NotificationType;
import com.sheout.notifications.internal.SendFailure;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosApi;
import com.sheout.notifications.SosError;
import com.sheout.notifications.SosStatus;
import com.sheout.notifications.internal.channel.NotificationChannel;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.logging.Redact;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.EmergencyContact;
import com.sheout.users.EmergencyContactsApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Texting her emergency contacts is deliberately NOT routed through domain
 * events, unlike the rest of this module (see NotificationEventListeners) -
 * the spec calls for SOS to be a fast, direct, synchronous call with no
 * retry/backoff, since an alert cannot be allowed to sit in a queue. The
 * contact texts run entirely on the caller's (SosController's) request
 * thread. Operators are the exception: they are told through SosAlertRaised,
 * so a slow push to their devices never delays her contacts' texts.
 * <p>
 * ALSO NOT using the REQUIRES_NEW-via-injected-proxy dance NotificationLogService
 * needs - and that's a deliberate distinction, not an oversight. That
 * pattern exists specifically to escape the stale transaction
 * synchronization an AFTER_COMMIT listener runs inside (see
 * NotificationLogService's Javadoc). trigger() is a plain method called
 * directly from the controller with no ambient transaction and no prior
 * commit on this thread to leave anything stale behind, so every
 * repository .save() below - called directly on the injected repository
 * bean, never self-invoked - already gets its own genuine transaction from
 * Spring Data's repository proxy, no extra annotation needed. Also
 * deliberately NOT one big @Transactional wrapping the whole method: that
 * would hold a DB transaction open across N sequential Twilio HTTP calls,
 * the same "external call blocking a DB transaction" problem payments'
 * RazorpayPaymentGateway call was kept out of a transaction for.
 * <p>
 * ASSUMPTION FLAGGED: bookingId (if given) is not validated against
 * BookingApi - it's stored purely for admin context on the alert record.
 * Adding a cross-module lookup here would add latency and a new failure
 * mode to a safety-critical path for a check whose payoff (rejecting a
 * bogus/foreign bookingId) is low.
 */
@Service
public class SosService implements SosApi {

    private static final Logger log = LoggerFactory.getLogger(SosService.class);

    private final SosAlertRepository sosAlertRepository;
    private final NotificationLogService notificationLogService;
    private final NotificationChannel smsChannel;
    private final DomainEventPublisher eventPublisher;
    private final CustomerProfileApi customerProfileApi;
    private final EmergencyContactsApi emergencyContactsApi;
    private final SosFanOutThrottle fanOutThrottle;
    private final NotificationCopy copy;

    public SosService(SosAlertRepository sosAlertRepository, NotificationLogService notificationLogService,
                       List<NotificationChannel> channels, CustomerProfileApi customerProfileApi,
                       EmergencyContactsApi emergencyContactsApi, SosFanOutThrottle fanOutThrottle,
                       DomainEventPublisher eventPublisher, NotificationCopy copy) {
        this.copy = copy;
        this.fanOutThrottle = fanOutThrottle;
        this.sosAlertRepository = sosAlertRepository;
        this.notificationLogService = notificationLogService;
        // Contacts are texted: they are not SheOut users and have no device
        // to push to, and SMS is the one channel that reaches any phone.
        this.smsChannel = channels.stream()
                .filter(channel -> channel.type() == NotificationChannelType.SMS)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SOS needs an SMS channel"));
        this.eventPublisher = eventPublisher;
        this.customerProfileApi = customerProfileApi;
        this.emergencyContactsApi = emergencyContactsApi;
    }

    public SosOutcome trigger(UUID customerAccountId, double lat, double lng, UUID bookingId) {
        String customerName = customerProfileApi.findByAccountId(customerAccountId)
                .map(profile -> profile.name())
                .filter(name -> name != null && !name.isBlank())
                .orElse("A SheOut user");
        List<EmergencyContact> contacts = emergencyContactsApi.findContactsByAccountId(customerAccountId);

        // Persisted immediately, before any contact fan-out attempt, so the
        // trigger itself is recorded (and visible via GET /active) even if
        // every send below fails, or even if there are zero contacts to try.
        SosAlertEntity alert = sosAlertRepository.save(new SosAlertEntity(customerAccountId, bookingId, lat, lng));

        // Operators hear about every press, before any throttling decision
        // below - a throttle on re-texting contacts is not a throttle on
        // telling the people whose job is to respond.
        eventPublisher.publish(new SosAlertRaised(alert.getId(), customerAccountId, bookingId));

        // Never a refusal - see SosFanOutThrottle. The alert above is already
        // saved; this only decides whether contacts texted moments ago are
        // texted again right now.
        //
        // Held back only if a text really reached somebody within the last
        // minute. The throttle counts presses, not deliveries, so on its own
        // it would tell a woman whose every send had failed that her contacts
        // "were texted less than a minute ago" - and stop retrying. When
        // nothing has got through, every press tries again.
        if (!contacts.isEmpty()
                && !fanOutThrottle.shouldTextContacts(customerAccountId)
                && sosAlertRepository.existsByCustomerAccountIdAndContactsNotifiedGreaterThanAndCreatedAtAfter(
                        customerAccountId, 0, Instant.now().minus(SosFanOutThrottle.SPACING))) {
            log.warn("SOS alert {} recorded; contacts were texted within the last minute after repeated presses, not re-texted",
                    alert.getId());
            return new SosOutcome(alert.getId(), contacts.size(), 0, List.of(), true);
        }

        List<SosOutcome.ContactOutcome> outcomes = new ArrayList<>();
        int notified = 0;
        OutboundMessage message = OutboundMessage.of(
                "SOS from " + customerName, "Location: maps.google.com/?q=" + lat + "," + lng, null);
        List<ContactDelivery> attempts = new ArrayList<>();

        for (EmergencyContact contact : contacts) {
            Result<Void, SendFailure> result = smsChannel.send(contact.phoneNumber(), message);
            boolean delivered = result.isSuccess();
            if (delivered) {
                notified++;
            } else {
                // Never silently swallowed - a failed SOS send is logged clearly,
                // at ERROR, with which contact and why. Which contact is its id,
                // not her name: the log is read far more widely than the
                // contacts table. The provider's error text can quote the
                // number, so it is redacted too.
                log.error("SOS alert {}: SMS to contact {} failed - {}",
                        alert.getId(), contact.id(), Redact.phoneNumbersIn(String.valueOf(result.error())));
            }
            outcomes.add(new SosOutcome.ContactOutcome(contact.name(), contact.relationship(), delivered));
            attempts.add(new ContactDelivery(contact.phoneNumber(), result));
        }

        // Her own record of the alert, in her inbox, with one delivery per
        // contact - written after the sends so it can say how many got it.
        UUID notificationId = notificationLogService.recordNotification(customerAccountId, NotificationType.SOS_ALERT,
                sosRecord(customerAccountId, contacts.size(), notified));
        attempts.forEach(attempt -> notificationLogService.recordDelivery(
                notificationId, NotificationChannelType.SMS, attempt.phoneNumber(), attempt.result()));

        alert.setContactsNotified(notified);
        alert.setContactsFailed(contacts.size() - notified);
        sosAlertRepository.save(alert);

        return new SosOutcome(alert.getId(), contacts.size(), notified, outcomes, false);
    }

    @Override
    public List<SosAlertSummary> findActiveAlerts() {
        return sosAlertRepository.findByStatusOrderByCreatedAtDesc(SosStatus.ACTIVE).stream()
                .map(SosService::toSummary)
                .toList();
    }

    @Override
    public List<SosAlertSummary> findByBookingId(UUID bookingId) {
        return sosAlertRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(SosService::toSummary)
                .toList();
    }

    /**
     * Transactional, unlike trigger() above - this is a single short write
     * with no external call in it, so the reasoning that kept trigger()
     * out of one transaction (N sequential Twilio calls) does not apply.
     */
    @Override
    @Transactional
    public Result<SosAlertSummary, SosError> resolve(UUID alertId, UUID resolvedByAccountId) {
        Optional<SosAlertEntity> found = sosAlertRepository.findById(alertId);
        if (found.isEmpty()) {
            return Result.failure(SosError.ALERT_NOT_FOUND);
        }
        SosAlertEntity alert = found.get();
        if (alert.getStatus() == SosStatus.RESOLVED) {
            return Result.failure(SosError.ALREADY_RESOLVED);
        }
        alert.resolve(resolvedByAccountId);
        log.info("SOS alert {} resolved by admin {}", alertId, resolvedByAccountId);
        return Result.success(toSummary(sosAlertRepository.save(alert)));
    }

    private static SosAlertSummary toSummary(SosAlertEntity alert) {
        return new SosAlertSummary(
                alert.getId(),
                alert.getCustomerAccountId(),
                alert.getBookingId(),
                alert.getLat(),
                alert.getLng(),
                alert.getStatus(),
                alert.getContactsNotified(),
                alert.getContactsFailed(),
                alert.getCreatedAt(),
                alert.getResolvedAt(),
                alert.getResolvedBy()
        );
    }

    /**
     * ASSUMPTION FLAGGED on shape: {@code success} is true iff at least one
     * contact was actually notified - not specified by the spec, decided
     * here so the frontend has one boolean to check regardless of *why*
     * nothing went out (zero contacts configured vs. every send failing),
     * rather than needing to special-case an HTTP status code for a
     * safety-critical response. See SosController's Javadoc for the same
     * reasoning applied to the endpoint's HTTP status.
     */
    /** Her own record of the alert, in her language with the English beneath - see NotificationCopy.safety. */
    private OutboundMessage sosRecord(UUID customerAccountId, int contacts, int notified) {
        AppLanguage language = copy.languageOf(customerAccountId);
        String key = contacts == 0 ? "sosNoContacts"
                : notified == 0 ? "sosNoneReached"
                : contacts == 1 ? "sosOneReached"
                : "sosSomeReached";
        Map<String, String> params = Map.of("notified", String.valueOf(notified), "total", String.valueOf(contacts));
        return OutboundMessage.of(copy.safety(language, "sosSent.title", Map.of()), copy.safety(language, key, params), "/sos");
    }

    private record ContactDelivery(String phoneNumber, Result<Void, SendFailure> result) {
    }

    public record SosOutcome(UUID alertId, int contactsTotal, int contactsNotified, List<ContactOutcome> contacts,
                             boolean contactsRecentlyTexted) {

        /**
         * A press whose re-text was spaced out still counts as a success: her
         * contacts were texted with her location less than a minute ago, and
         * showing her a red failure in that moment would be false and would
         * push her to keep pressing.
         */
        public boolean success() {
            return contactsNotified > 0 || contactsRecentlyTexted;
        }

        public record ContactOutcome(String contactName, String relationship, boolean delivered) {
        }
    }
}
