package com.sheout.notifications.internal.sos;

import com.sheout.notifications.SosDeliveryChannel;
import com.sheout.notifications.SosTriggerSource;
import com.sheout.notifications.internal.NotificationCopy;
import com.sheout.notifications.internal.NotificationChannelType;
import com.sheout.notifications.internal.NotificationLogService;
import com.sheout.notifications.internal.channel.NotificationChannel;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.users.AppLanguage;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.EmergencyContact;
import com.sheout.users.EmergencyContactsApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How an SOS arrived is recorded with it, and the same press arriving twice -
 * a retry after a lost response, or the offline queue sending what data had
 * already delivered - never texts her contacts twice.
 */
class SosDeliveryTest {

    private final SosAlertRepository alerts = mock(SosAlertRepository.class);
    private final NotificationChannel sms = mock(NotificationChannel.class);
    private final EmergencyContactsApi contacts = mock(EmergencyContactsApi.class);
    private final SosFanOutThrottle throttle = mock(SosFanOutThrottle.class);
    private final NotificationCopy copy = mock(NotificationCopy.class);
    private final List<SosAlertEntity> saved = new ArrayList<>();
    private final UUID rider = UUID.randomUUID();
    private SosService service;

    @BeforeEach
    void setUp() {
        when(sms.type()).thenReturn(NotificationChannelType.SMS);
        when(sms.send(anyString(), any())).thenReturn(Result.success(null));
        when(contacts.findContactsByAccountId(rider)).thenReturn(List.of(
                new EmergencyContact(UUID.randomUUID(), rider, "Amma", "+919000000001", "Mother")));
        when(throttle.shouldTextContacts(any())).thenReturn(true);
        when(copy.languageOf(any())).thenReturn(AppLanguage.EN);
        when(copy.safety(any(), anyString(), any())).thenReturn("text");
        when(alerts.save(any())).thenAnswer(inv -> {
            SosAlertEntity a = inv.getArgument(0);
            if (!saved.contains(a)) saved.add(a);
            return a;
        });
        when(alerts.findByClientAlertId(any())).thenAnswer(inv -> saved.stream()
                .filter(a -> inv.getArgument(0).equals(a.getClientAlertId())).findFirst());
        CustomerProfileApi profiles = mock(CustomerProfileApi.class);
        when(profiles.findByAccountId(any())).thenReturn(Optional.empty());
        service = new SosService(alerts, mock(NotificationLogService.class), List.of(sms), profiles, contacts, throttle,
                mock(DomainEventPublisher.class), copy);
    }

    @Test
    void aDiscreetAlertFromTheOfflineQueueRecordsHowItCame() {
        UUID clientId = UUID.randomUUID();
        Instant raised = Instant.now().minusSeconds(240);

        service.trigger(rider, 17.44, 78.39, null,
                new SosService.Delivery(SosTriggerSource.SHAKE, SosDeliveryChannel.DELAYED_QUEUE, raised, clientId, true));

        ArgumentCaptor<SosAlertEntity> alert = ArgumentCaptor.forClass(SosAlertEntity.class);
        verify(alerts, times(2)).save(alert.capture());
        SosAlertEntity recorded = alert.getValue();
        assertThat(recorded.getTriggerSource()).isEqualTo(SosTriggerSource.SHAKE);
        assertThat(recorded.getDeliveryChannel()).isEqualTo(SosDeliveryChannel.DELAYED_QUEUE);
        assertThat(recorded.isSmsFallbackOpened()).isTrue();
        assertThat(recorded.getTriggeredAt()).isEqualTo(raised);
        assertThat(recorded.getContactsNotified()).isEqualTo(1);
    }

    @Test
    void theSamePressArrivingAgainIsTheSameAlertAndNobodyIsTextedTwice() {
        UUID clientId = UUID.randomUUID();
        service.trigger(rider, 17.44, 78.39, null,
                new SosService.Delivery(SosTriggerSource.BUTTON, SosDeliveryChannel.DATA, Instant.now(), clientId, false));

        // The phone did not hear back in time, opened the SMS app, and says so on the retry.
        SosService.SosOutcome again = service.trigger(rider, 17.44, 78.39, null,
                new SosService.Delivery(SosTriggerSource.BUTTON, SosDeliveryChannel.DELAYED_QUEUE, Instant.now(), clientId, true));

        verify(sms, times(1)).send(anyString(), any());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).isSmsFallbackOpened()).as("the later report is added to the first alert").isTrue();
        assertThat(saved.get(0).getDeliveryChannel()).as("it first arrived over data").isEqualTo(SosDeliveryChannel.DATA);
        assertThat(again.success()).isTrue();
        assertThat(again.alertId()).isEqualTo(saved.get(0).getId());
    }

    @Test
    void anOldAppWithoutTheNewFieldsIsAnOrdinaryButtonPress() {
        service.trigger(rider, 17.44, 78.39, null);

        assertThat(saved.get(0).getTriggerSource()).isEqualTo(SosTriggerSource.BUTTON);
        assertThat(saved.get(0).getDeliveryChannel()).isEqualTo(SosDeliveryChannel.DATA);
        assertThat(saved.get(0).getClientAlertId()).isNull();
    }
}
