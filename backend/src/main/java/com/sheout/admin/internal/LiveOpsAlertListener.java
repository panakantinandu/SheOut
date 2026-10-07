package com.sheout.admin.internal;

import com.sheout.booking.TripAlertRaised;
import com.sheout.notifications.SosAlertRaised;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffLiveEvents;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A new SOS or trip alert, pushed at once to every open console whose staff
 * member can respond (sos.respond). Phone pushes still go out separately
 * (notifications); this is the console banner, for whoever is at a desk.
 * <p>
 * Ids and the kind only. The console opens the alert through the normal
 * endpoints, which apply masking and the agent's scope as always.
 */
@Component
class LiveOpsAlertListener {

    private final StaffLiveEvents live;

    LiveOpsAlertListener(StaffLiveEvents live) {
        this.live = live;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSos(SosAlertRaised event) {
        live.publish("sos", Permission.SOS_RESPOND, "{\"alertId\":\"" + event.alertId() + "\",\"duringTrip\":"
                + (event.bookingId() != null) + "}");
    }

    /** A start-of-shift check held for a person: every console that reviews verification hears at once. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onShiftCheckHeld(com.sheout.driververification.ShiftCheckNeedsReview event) {
        live.publish("shift-check-held", Permission.VERIFICATION_REVIEW, "{\"accountId\":\"" + event.accountId() + "\"}");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTripAlert(TripAlertRaised event) {
        live.publish("trip-alert", Permission.SOS_RESPOND, "{\"alertId\":\"" + event.alertId() + "\",\"bookingId\":\""
                + event.bookingId() + "\",\"kind\":\"" + event.kind() + "\"}");
    }
}
