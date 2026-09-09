package com.sheout.notifications.internal.sos;

import com.sheout.notifications.internal.NotificationChannelType;
import com.sheout.notifications.internal.NotificationError;
import com.sheout.notifications.internal.NotificationLogEntity;
import com.sheout.notifications.internal.NotificationLogRepository;
import com.sheout.notifications.internal.NotificationStatus;
import com.sheout.notifications.internal.NotificationType;
import com.sheout.notifications.internal.channel.NotificationChannel;
import com.sheout.sharedkernel.Result;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.EmergencyContact;
import com.sheout.users.EmergencyContactsApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deliberately NOT routed through domain events, unlike the rest of this
 * module (see NotificationEventListeners) - the spec calls for SOS to be a
 * fast, direct, synchronous call with no retry/backoff, since an alert
 * cannot be allowed to sit in a queue. trigger() runs entirely on the
 * caller's (SosController's) request thread.
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
public class SosService {

    private static final Logger log = LoggerFactory.getLogger(SosService.class);

    private final SosAlertRepository sosAlertRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationChannel smsChannel;
    private final CustomerProfileApi customerProfileApi;
    private final EmergencyContactsApi emergencyContactsApi;

    public SosService(SosAlertRepository sosAlertRepository, NotificationLogRepository notificationLogRepository,
                       NotificationChannel smsChannel, CustomerProfileApi customerProfileApi,
                       EmergencyContactsApi emergencyContactsApi) {
        this.sosAlertRepository = sosAlertRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.smsChannel = smsChannel;
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

        List<SosOutcome.ContactOutcome> outcomes = new ArrayList<>();
        int notified = 0;
        String message = "SOS ALERT from SheOut: " + customerName
                + " may need help and shared this location: https://www.google.com/maps?q=" + lat + "," + lng;

        for (EmergencyContact contact : contacts) {
            Result<Void, NotificationError> result = smsChannel.send(contact.phoneNumber(), message);
            boolean delivered = result.isSuccess();
            if (delivered) {
                notified++;
            } else {
                // Never silently swallowed - a failed SOS send is logged clearly, at ERROR, with which contact and why.
                log.error("SOS alert {}: SMS to contact '{}' ({}) failed - {}",
                        alert.getId(), contact.name(), contact.relationship(), result.error());
            }
            outcomes.add(new SosOutcome.ContactOutcome(contact.name(), contact.relationship(), delivered));
            notificationLogRepository.save(new NotificationLogEntity(
                    customerAccountId, contact.phoneNumber(), NotificationType.SOS_ALERT, NotificationChannelType.SMS,
                    delivered ? NotificationStatus.SENT : NotificationStatus.FAILED,
                    delivered ? null : result.error().toString()));
        }

        alert.setContactsNotified(notified);
        alert.setContactsFailed(contacts.size() - notified);
        sosAlertRepository.save(alert);

        return new SosOutcome(alert.getId(), contacts.size(), notified, outcomes);
    }

    public List<SosAlertEntity> findActive() {
        return sosAlertRepository.findByStatusOrderByCreatedAtDesc(SosStatus.ACTIVE);
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
    public record SosOutcome(UUID alertId, int contactsTotal, int contactsNotified, List<ContactOutcome> contacts) {

        public boolean success() {
            return contactsNotified > 0;
        }

        public record ContactOutcome(String contactName, String relationship, boolean delivered) {
        }
    }
}
