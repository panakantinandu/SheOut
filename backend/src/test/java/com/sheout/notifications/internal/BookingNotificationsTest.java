package com.sheout.notifications.internal;

import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.CancellationReason;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.users.AccountLanguageApi;
import com.sheout.users.AppLanguage;
import com.sheout.users.DriverProfileApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a cancellation puts in whose inbox, and on whose phone. The inbox is
 * a rider's history: the last thing it says about a booking must still be
 * true - never "we're finding you a partner" for a search she stopped.
 */
class BookingNotificationsTest {

    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final AccountLanguageApi languages = mock(AccountLanguageApi.class);
    private final NotificationEventListeners listeners = new NotificationEventListeners(dispatcher,
            mock(DriverProfileApi.class), mock(BookingApi.class), new NotificationCopy(languages));

    private final UUID booking = UUID.randomUUID();
    private final UUID rider = UUID.randomUUID();
    private final UUID partner = UUID.randomUUID();

    @Test
    void aRiderWhoCancelsGetsAQuietLineInHerInboxAndHerPartnerIsPushed() {
        when(languages.languageOf(any())).thenReturn(Optional.empty());

        listeners.onBookingCancelled(new BookingCancelled(booking, rider, partner, rider, CancellationReason.CHANGE_OF_PLANS));

        ArgumentCaptor<OutboundMessage> inbox = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(dispatcher).record(eq(rider), eq(NotificationType.BOOKING_CANCELLED), inbox.capture());
        assertThat(inbox.getValue().title()).isEqualTo("You cancelled your booking");
        assertThat(inbox.getValue().body()).isEqualTo("Nothing was charged. Book again whenever you are ready.");
        assertThat(inbox.getValue().link()).isEqualTo("/tracking/" + booking);
        // Not pushed to her - she just tapped it.
        verify(dispatcher, never()).deliver(eq(rider), any(), any());
        verify(dispatcher).deliver(eq(partner), eq(NotificationType.BOOKING_CANCELLED), any());
    }

    @Test
    void aRiderWhoCancelsASearchNobodyTookHasItInHerLanguage() {
        when(languages.languageOf(rider)).thenReturn(Optional.of(AppLanguage.TE));

        listeners.onBookingCancelled(new BookingCancelled(booking, rider, null, rider, CancellationReason.CHANGE_OF_PLANS));

        ArgumentCaptor<OutboundMessage> inbox = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(dispatcher).record(eq(rider), eq(NotificationType.BOOKING_CANCELLED), inbox.capture());
        assertThat(inbox.getValue().title()).isEqualTo("మీరు మీ బుకింగ్‌ను రద్దు చేశారు");
        verify(dispatcher, never()).deliver(any(), any(), any());
    }

    @Test
    void whenHerPartnerCancelsSheIsPushedAndNothingIsRecordedQuietly() {
        when(languages.languageOf(any())).thenReturn(Optional.empty());

        listeners.onBookingCancelled(new BookingCancelled(booking, rider, partner, partner, CancellationReason.CHANGE_OF_PLANS));

        verify(dispatcher).deliver(eq(rider), eq(NotificationType.BOOKING_CANCELLED), any());
        verify(dispatcher, never()).record(any(), any(), any());
        verify(dispatcher, never()).deliver(eq(partner), any(), any());
    }
}
