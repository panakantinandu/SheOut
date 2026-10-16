package com.sheout.notifications.internal.sos;

import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosError;
import com.sheout.notifications.SosResolution;
import com.sheout.notifications.SosStatus;
import com.sheout.notifications.internal.NotificationCopy;
import com.sheout.notifications.internal.NotificationChannelType;
import com.sheout.notifications.internal.NotificationLogService;
import com.sheout.notifications.internal.channel.NotificationChannel;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.EmergencyContactsApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Closing an SOS records what the operator found, not only who closed it -
 * and a second close cannot rewrite the first.
 */
class SosResolveTest {

    private final SosAlertRepository alerts = mock(SosAlertRepository.class);
    private final SosService service = new SosService(alerts, mock(NotificationLogService.class), List.of(sms()),
            mock(CustomerProfileApi.class), mock(EmergencyContactsApi.class), mock(SosFanOutThrottle.class),
            mock(DomainEventPublisher.class), mock(NotificationCopy.class));

    @Test
    void theOutcomeAndNoteAreKeptWithTheAlert() {
        SosAlertEntity alert = active();
        UUID operator = UUID.randomUUID();

        Result<SosAlertSummary, SosError> result = service.resolve(alertId, operator,
                SosResolution.FALSE_ALARM, "Called her, pressed it by mistake in her bag");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().status()).isEqualTo(SosStatus.RESOLVED);
        assertThat(result.value().resolvedBy()).isEqualTo(operator);
        assertThat(result.value().resolutionOutcome()).isEqualTo(SosResolution.FALSE_ALARM);
        assertThat(result.value().resolutionNote()).isEqualTo("Called her, pressed it by mistake in her bag");
    }

    @Test
    void aSecondCloseDoesNotRewriteTheFirst() {
        SosAlertEntity alert = active();
        UUID first = UUID.randomUUID();
        service.resolve(alertId, first, SosResolution.POLICE_INVOLVED, "112 called, police reached her");

        Result<SosAlertSummary, SosError> again = service.resolve(alertId, UUID.randomUUID(),
                SosResolution.FALSE_ALARM, null);

        assertThat(again.isFailure()).isTrue();
        assertThat(again.error()).isEqualTo(SosError.ALREADY_RESOLVED);
        assertThat(alert.getResolvedBy()).isEqualTo(first);
        assertThat(alert.getResolutionOutcome()).isEqualTo(SosResolution.POLICE_INVOLVED);
    }

    private static NotificationChannel sms() {
        NotificationChannel sms = mock(NotificationChannel.class);
        when(sms.type()).thenReturn(NotificationChannelType.SMS);
        return sms;
    }

    private final UUID alertId = UUID.randomUUID();

    private SosAlertEntity active() {
        SosAlertEntity alert = new SosAlertEntity(UUID.randomUUID(), null, 17.44, 78.39);
        when(alerts.findById(alertId)).thenReturn(Optional.of(alert));
        when(alerts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return alert;
    }
}
