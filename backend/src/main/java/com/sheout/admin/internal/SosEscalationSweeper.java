package com.sheout.admin.internal;

import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosApi;
import com.sheout.notifications.SosEscalationApi;
import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffDirectory;
import com.sheout.staff.StaffLiveEvents;
import com.sheout.staff.WorkAssignments;
import com.sheout.staff.WorkSla;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An SOS nobody has taken does not just turn red: everyone who can respond is
 * told again, and again, until someone takes it.
 * <p>
 * The first push goes out when the SOS is raised (notifications). If nobody
 * has taken it from the SOS queue within the acknowledgement target
 * (sheout.staff.sla.sos-ack-minutes, 2), every active member of staff who can
 * respond - safety responders, managers, owners - gets an urgent push, and
 * every open console rings again even where it was silenced. That repeats
 * every repeat-minutes until someone takes it or it is resolved, up to
 * max-rounds. "Acknowledge" on a console's banner only quiets that one
 * screen; taking the SOS is what tells everyone else somebody is on it.
 * <p>
 * Stateless: round n of an alert is due once the alert has waited
 * ack + (n - 1) * repeat, and each round is claimed once across servers, so a
 * restart or a second server neither skips a round nor sends one twice.
 */
@Component
class SosEscalationSweeper {

    private static final Logger log = LoggerFactory.getLogger(SosEscalationSweeper.class);

    private final SosApi sos;
    private final WorkAssignments work;
    private final WorkSla sla;
    private final StaffDirectory staff;
    private final SosEscalationApi pushes;
    private final StaffLiveEvents live;
    private final ClusterLock claims;
    private final boolean enabled;
    private final Duration repeat;
    private final int maxRounds;

    SosEscalationSweeper(SosApi sos, WorkAssignments work, WorkSla sla, StaffDirectory staff,
                         SosEscalationApi pushes, StaffLiveEvents live, ClusterLock claims,
                         @Value("${sheout.sos-escalation.enabled:true}") boolean enabled,
                         @Value("${sheout.sos-escalation.repeat-minutes:2}") long repeatMinutes,
                         @Value("${sheout.sos-escalation.max-rounds:30}") int maxRounds) {
        this.sos = sos;
        this.work = work;
        this.sla = sla;
        this.staff = staff;
        this.pushes = pushes;
        this.live = live;
        this.claims = claims;
        this.enabled = enabled;
        this.repeat = Duration.ofMinutes(Math.max(1, repeatMinutes));
        this.maxRounds = Math.max(1, maxRounds);
    }

    @Scheduled(initialDelayString = "${sheout.sos-escalation.initial-delay-ms:30000}",
            fixedDelayString = "${sheout.sos-escalation.interval-ms:20000}")
    public void sweep() {
        if (enabled) {
            try {
                runOnce(Instant.now());
            } catch (RuntimeException ex) {
                log.error("SOS escalation sweep failed: {}", ex.toString());
            }
        }
    }

    /** One pass. Package-visible for tests, which pass the clock. Returns how many reminders went out. */
    int runOnce(Instant now) {
        List<SosAlertSummary> active = sos.findActiveAlerts();
        if (active.isEmpty()) {
            return 0;
        }
        Map<UUID, WorkAssignments.Held> taken = work.held(WorkAssignments.Kind.SOS);
        List<UUID> responders = null;
        int sent = 0;
        for (SosAlertSummary alert : active) {
            if (taken.containsKey(alert.id())) {
                continue;
            }
            int round = dueRound(Duration.between(alert.createdAt(), now));
            if (round == 0 || !claims.firstToClaim("sos-escalation:" + alert.id() + ":" + round, Duration.ofHours(6), true)) {
                continue;
            }
            if (responders == null) {
                responders = staff.activeWith(Permission.SOS_RESPOND).stream()
                        .map(StaffDirectory.StaffMember::accountId).toList();
            }
            long minutes = Math.max(1, Duration.between(alert.createdAt(), now).toMinutes());
            boolean duringTrip = alert.bookingId() != null;
            log.warn("SOS {} untaken for {} min: reminder {} to {} responder(s)", alert.id(), minutes, round, responders.size());
            pushes.pushUnanswered(responders, alert.id(), duringTrip, minutes);
            live.publish("sos-escalated", Permission.SOS_RESPOND, "{\"alertId\":\"" + alert.id() + "\",\"round\":" + round
                    + ",\"minutes\":" + minutes + ",\"duringTrip\":" + duringTrip + "}");
            sent++;
        }
        return sent;
    }

    /** 0 while within the target; then 1, 2, ... one per repeat interval, capped at maxRounds. */
    int dueRound(Duration waited) {
        Duration over = waited.minus(sla.sosAck());
        if (over.isNegative()) {
            return 0;
        }
        long round = over.toMillis() / repeat.toMillis() + 1;
        return (int) Math.min(round, maxRounds);
    }
}
