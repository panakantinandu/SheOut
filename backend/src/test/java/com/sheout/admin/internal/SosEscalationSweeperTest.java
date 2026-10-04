package com.sheout.admin.internal;

import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosApi;
import com.sheout.notifications.SosDeliveryChannel;
import com.sheout.notifications.SosEscalationApi;
import com.sheout.notifications.SosStatus;
import com.sheout.notifications.SosTriggerSource;
import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffDirectory;
import com.sheout.staff.StaffLiveEvents;
import com.sheout.staff.StaffRole;
import com.sheout.staff.WorkAssignments;
import com.sheout.staff.WorkSla;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Every id here is made up. */
class SosEscalationSweeperTest {

    private final Instant raised = Instant.parse("2026-10-04T10:00:00Z");
    private final UUID alertId = UUID.randomUUID();
    private final UUID responder = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();

    private final List<SosAlertSummary> active = new ArrayList<>();
    private final Map<UUID, WorkAssignments.Held> held = new HashMap<>();
    private final Set<String> claimed = new HashSet<>();
    private final List<String> pushed = new ArrayList<>();
    private final List<String> liveEvents = new ArrayList<>();
    private SosEscalationSweeper sweeper;

    @BeforeEach
    void setUp() {
        SosApi sos = mock(SosApi.class);
        when(sos.findActiveAlerts()).thenAnswer(i -> List.copyOf(active));
        WorkAssignments work = mock(WorkAssignments.class);
        when(work.held(WorkAssignments.Kind.SOS)).thenAnswer(i -> Map.copyOf(held));
        WorkSla sla = mock(WorkSla.class);
        when(sla.sosAck()).thenReturn(Duration.ofMinutes(2));
        StaffDirectory directory = mock(StaffDirectory.class);
        when(directory.activeWith(Permission.SOS_RESPOND)).thenReturn(List.of(
                new StaffDirectory.StaffMember(responder, "Responder", StaffRole.SAFETY_RESPONDER),
                new StaffDirectory.StaffMember(manager, "Manager", StaffRole.MANAGER)));
        ClusterLock claims = mock(ClusterLock.class);
        when(claims.firstToClaim(anyString(), any(), anyBoolean())).thenAnswer(i -> claimed.add(i.getArgument(0)));
        SosEscalationApi pushes = (ids, id, duringTrip, minutes) ->
                pushed.add(id + " to " + ids.size() + " after " + minutes + " min" + (duringTrip ? " on a trip" : ""));
        StaffLiveEvents live = (type, audience, json) -> liveEvents.add(type + " " + audience + " " + json);
        sweeper = new SosEscalationSweeper(sos, work, sla, directory, pushes, live, claims, true, 2, 3);
        active.add(new SosAlertSummary(alertId, UUID.randomUUID(), UUID.randomUUID(), 17.38, 78.48, SosStatus.ACTIVE,
                1, 0, raised, null, null, SosTriggerSource.BUTTON, SosDeliveryChannel.DATA, false, raised));
    }

    @Test
    void nothingWithinTheTwoMinuteTarget() {
        assertThat(sweeper.runOnce(raised.plusSeconds(119))).isZero();
        assertThat(pushed).isEmpty();
        assertThat(liveEvents).isEmpty();
    }

    @Test
    void anUntakenSosRemindsEveryResponderOncePerRoundUntilTheCap() {
        assertThat(sweeper.runOnce(raised.plusSeconds(120))).isEqualTo(1);
        assertThat(pushed).containsExactly(alertId + " to 2 after 2 min on a trip");
        assertThat(liveEvents).singleElement().asString()
                .startsWith("sos-escalated " + Permission.SOS_RESPOND).contains(alertId.toString(), "\"round\":1");

        // The same round is not sent twice, by this sweep or another server's.
        assertThat(sweeper.runOnce(raised.plusSeconds(150))).isZero();

        assertThat(sweeper.runOnce(raised.plusSeconds(240))).isEqualTo(1);
        assertThat(sweeper.runOnce(raised.plusSeconds(360))).isEqualTo(1);
        // max-rounds is 3 here: the fourth interval sends nothing more.
        assertThat(sweeper.runOnce(raised.plusSeconds(480))).isZero();
        assertThat(pushed).hasSize(3);
    }

    @Test
    void takingTheSosStopsTheReminders() {
        sweeper.runOnce(raised.plusSeconds(120));
        held.put(alertId, new WorkAssignments.Held(responder, raised.plusSeconds(130)));
        assertThat(sweeper.runOnce(raised.plusSeconds(240))).isZero();
        assertThat(pushed).hasSize(1);
    }

    @Test
    void aResolvedSosIsNoLongerChased() {
        active.clear();
        assertThat(sweeper.runOnce(raised.plusSeconds(600))).isZero();
        assertThat(pushed).isEmpty();
    }
}
