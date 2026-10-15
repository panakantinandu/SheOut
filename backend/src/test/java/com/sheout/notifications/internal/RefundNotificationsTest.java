package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.notifications.internal.channel.NotificationChannel;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.payments.RefundIssued;
import com.sheout.sharedkernel.Result;
import com.sheout.users.AccountLanguageApi;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Money back is told by push, in her inbox, and by text message every time -
 * not only when the push did not get through.
 */
class RefundNotificationsTest {

    private final UUID rider = UUID.randomUUID();
    private final UUID booking = UUID.randomUUID();

    @Test
    void aWalletRefundSaysHowMuchAndHerNewBalanceAndOpensHerWallet() {
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        AccountLanguageApi languages = mock(AccountLanguageApi.class);
        when(languages.languageOf(any())).thenReturn(Optional.empty());
        NotificationEventListeners listeners = new NotificationEventListeners(dispatcher, mock(DriverProfileApi.class),
                mock(BookingApi.class), new NotificationCopy(languages));

        listeners.onRefundIssued(new RefundIssued(rider, new BigDecimal("120"), RefundIssued.Destination.SHEOUT_WALLET,
                booking, new BigDecimal("340.5")));

        ArgumentCaptor<OutboundMessage> sent = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(dispatcher).deliver(eq(rider), eq(NotificationType.REFUND_ISSUED), sent.capture());
        assertThat(sent.getValue().title()).isEqualTo("₹120 refunded to your SheOut wallet");
        assertThat(sent.getValue().body()).isEqualTo("Your balance is now ₹340.50. Use it for your next ride.");
        assertThat(sent.getValue().link()).isEqualTo("/wallet");
        assertThat(sent.getValue().englishSms()).startsWith("SheOut: ₹120 refunded to your SheOut wallet.");
    }

    @Test
    void aRefundToHerBankSaysBanksTakeDaysAndOpensTheTrip() {
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        AccountLanguageApi languages = mock(AccountLanguageApi.class);
        when(languages.languageOf(any())).thenReturn(Optional.empty());
        NotificationEventListeners listeners = new NotificationEventListeners(dispatcher, mock(DriverProfileApi.class),
                mock(BookingApi.class), new NotificationCopy(languages));

        listeners.onRefundIssued(new RefundIssued(rider, new BigDecimal("77.09"), RefundIssued.Destination.ORIGINAL_METHOD, booking, null));

        ArgumentCaptor<OutboundMessage> sent = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(dispatcher).deliver(eq(rider), eq(NotificationType.REFUND_ISSUED), sent.capture());
        assertThat(sent.getValue().title()).isEqualTo("₹77.09 is on its way back to you");
        assertThat(sent.getValue().body()).contains("5 to 7 working days");
        assertThat(sent.getValue().link()).isEqualTo("/tracking/" + booking);
    }

    @Test
    void aRefundIsTextedEvenWhenThePushReachedHerPhoneAndOtherNewsIsNot() {
        NotificationChannel push = channel(com.sheout.notifications.internal.NotificationChannelType.PUSH);
        NotificationChannel sms = channel(com.sheout.notifications.internal.NotificationChannelType.SMS);
        NotificationLogService log = mock(NotificationLogService.class);
        when(log.recordNotification(any(), any(), any())).thenReturn(UUID.randomUUID());
        PushDeviceService devices = mock(PushDeviceService.class);
        when(devices.devicesFor(rider)).thenReturn(List.of(new PushDeviceEntity(rider, AccountRole.CUSTOMER, "token-1", "web", Instant.now())));
        AuthApi auth = mock(AuthApi.class);
        when(auth.findAccount(rider)).thenReturn(Optional.of(new AccountSummary(rider, "+919000000101", null, AccountRole.CUSTOMER, Instant.now(), false)));
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(push, sms), log, devices, auth,
                mock(CustomerProfileApi.class), mock(DriverProfileApi.class));
        OutboundMessage message = new OutboundMessage("t", "b", "/wallet", null, OutboundMessage.Urgency.NORMAL, "SheOut: t. b");

        assertThat(DeliveryPolicy.forType(NotificationType.REFUND_ISSUED).smsAlways()).isTrue();
        dispatcher.deliver(rider, NotificationType.REFUND_ISSUED, message);
        verify(push).send("token-1", message);
        verify(sms).send("+919000000101", message);

        // A trip update that reached her phone is not texted as well.
        NotificationChannel sms2 = channel(com.sheout.notifications.internal.NotificationChannelType.SMS);
        new NotificationDispatcher(List.of(push, sms2), log, devices, auth, mock(CustomerProfileApi.class), mock(DriverProfileApi.class))
                .deliver(rider, NotificationType.BOOKING_COMPLETED, message);
        verify(sms2, never()).send(anyString(), any());
    }

    private static NotificationChannel channel(com.sheout.notifications.internal.NotificationChannelType type) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.type()).thenReturn(type);
        when(channel.send(anyString(), any())).thenReturn(Result.success(null));
        return channel;
    }
}
