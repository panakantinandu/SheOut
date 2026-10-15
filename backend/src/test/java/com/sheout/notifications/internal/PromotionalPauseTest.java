package com.sheout.notifications.internal;

import com.sheout.auth.AuthApi;
import com.sheout.notifications.AnnouncementAudience;
import com.sheout.notifications.AnnouncementPurpose;
import com.sheout.notifications.AnnouncementRefusal;
import com.sheout.notifications.internal.channel.FcmPushChannel;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.FeatureWaitlistApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Promotional messages wait for an opt-in. With PROMOTIONAL_MESSAGES_ENABLED
 * off (the default), a promotional broadcast to a whole audience is refused
 * before anything is sent, and the "we miss you" nudger does not run.
 * Service notices still go out.
 */
class PromotionalPauseTest {

    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final FcmPushChannel push = mock(FcmPushChannel.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final CustomerProfileApi customers = mock(CustomerProfileApi.class);
    private final DriverProfileApi drivers = mock(DriverProfileApi.class);
    private final AuthApi auth = mock(AuthApi.class);
    private final FeatureWaitlistApi waitlist = mock(FeatureWaitlistApi.class);

    private AnnouncementService service(boolean promotionalEnabled) {
        when(announcements.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(push.sendToTopic(any(), any())).thenReturn(Result.success(null));
        when(customers.findEmailAddresses(anyInt(), anyInt())).thenReturn(List.of());
        when(auth.findEmailAddresses(any(), anyInt(), anyInt())).thenReturn(List.of());
        return new AnnouncementService(announcements, push, dispatcher, customers, drivers, auth, waitlist, 0, 2000,
                promotionalEnabled);
    }

    @Test
    void aPromotionalBroadcastIsRefusedWhilePausedAndNothingGoesOut() {
        var result = service(false).broadcast("20% off this weekend", "Book a ride and save.",
                AnnouncementAudience.ALL_CUSTOMERS, AnnouncementPurpose.PROMOTIONAL, false, UUID.randomUUID());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).isEqualTo(AnnouncementRefusal.PROMOTIONAL_PAUSED);
        verify(push, never()).sendToTopic(any(), any());
        verify(dispatcher, never()).sendEmail(any(), any());
        verify(announcements, never()).save(any());
    }

    @Test
    void aServiceNoticeStillGoesOutWhilePromotionsArePaused() {
        var result = service(false).broadcast("Heavy rain tonight", "Rides may take longer after 8 pm.",
                AnnouncementAudience.ALL_CUSTOMERS, AnnouncementPurpose.SERVICE, false, UUID.randomUUID());

        assertThat(result.isSuccess()).isTrue();
        verify(push).sendToTopic(any(), any());
    }

    @Test
    void aPromotionalBroadcastGoesOutOnceTheFlagIsOn() {
        var result = service(true).broadcast("20% off this weekend", "Book a ride and save.",
                AnnouncementAudience.ALL_CUSTOMERS, AnnouncementPurpose.PROMOTIONAL, false, UUID.randomUUID());

        assertThat(result.isSuccess()).isTrue();
        verify(push).sendToTopic(any(), any());
    }

    @Test
    void theWeMissYouNudgerDoesNotRunWhilePromotionsArePaused() {
        ClusterLock lock = mock(ClusterLock.class);
        ReengagementNudger nudger = new ReengagementNudger(mock(PushDeviceRepository.class), mock(ReengagementNudgeRepository.class),
                auth, drivers, mock(NotificationCopy.class), dispatcher, true, false, lock);

        nudger.sweep();

        verifyNoInteractions(lock);
    }
}
