package com.sheout.notifications.internal;

import com.sheout.auth.AuthApi;
import com.sheout.notifications.Announcement;
import com.sheout.notifications.AnnouncementAudience;
import com.sheout.notifications.internal.channel.FcmPushChannel;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.FeatureWaitlistApi;
import com.sheout.users.WaitlistFeature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The SheOut Seller launch reaches exactly the people who tapped "Notify me", each in her own inbox. */
class AnnouncementWaitlistTest {

    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final FcmPushChannel push = mock(FcmPushChannel.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final CustomerProfileApi customers = mock(CustomerProfileApi.class);
    private final DriverProfileApi drivers = mock(DriverProfileApi.class);
    private final AuthApi auth = mock(AuthApi.class);
    private final FeatureWaitlistApi waitlist = mock(FeatureWaitlistApi.class);
    private final AnnouncementService service = new AnnouncementService(announcements, push, dispatcher, customers, drivers,
            auth, waitlist, 0, 2000);

    @Test
    void theSellerWaitlistIsToldOneByOneAndNobodyElse() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(waitlist.interestedAccountIds(WaitlistFeature.SELLER)).thenReturn(List.of(first, second));
        when(announcements.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Announcement sent = service.broadcast("SheOut Seller is here", "Open the Seller tab to browse or start your shop.",
                AnnouncementAudience.SELLER_WAITLIST, false, UUID.randomUUID());

        ArgumentCaptor<OutboundMessage> message = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(dispatcher).deliver(eq(first), eq(NotificationType.ANNOUNCEMENT), message.capture());
        verify(dispatcher).deliver(eq(second), eq(NotificationType.ANNOUNCEMENT), any());
        verify(dispatcher, times(2)).deliver(any(), any(), any());
        assertThat(message.getValue().link()).isEqualTo("/seller");
        // Not the whole-audience channels: no topic push, no email to every rider.
        verify(push, never()).sendToTopic(any(), any());
        verify(dispatcher, never()).sendEmail(any(), any());
        assertThat(sent.audience()).isEqualTo(AnnouncementAudience.SELLER_WAITLIST);
    }
}
