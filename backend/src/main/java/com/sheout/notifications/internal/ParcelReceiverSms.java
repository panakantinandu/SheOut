package com.sheout.notifications.internal;

import com.sheout.booking.ParcelDeliveryCodeIssued;
import com.sheout.notifications.internal.channel.NotificationChannel;
import com.sheout.notifications.internal.channel.OutboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * Texts a parcel's delivery code to its receiver - only when
 * PARCEL_RECEIVER_SMS_ENABLED is on, which waits for DLT approval of the
 * template below. Until then (the default) nothing is sent and the sender
 * passes the code on from her app, which always shows it.
 * <p>
 * The receiver is not a SheOut user, so this goes straight to the SMS
 * channel, like an SOS contact. The code is never logged.
 */
@Component
class ParcelReceiverSms {

    private static final Logger log = LoggerFactory.getLogger(ParcelReceiverSms.class);

    private final Optional<NotificationChannel> sms;
    private final boolean enabled;

    ParcelReceiverSms(List<NotificationChannel> channels,
                      @Value("${sheout.parcel.receiver-sms-enabled:false}") boolean enabled) {
        this.sms = channels.stream().filter(c -> c.type() == NotificationChannelType.SMS).findFirst();
        this.enabled = enabled;
    }

    @Async(NotificationDeliveryConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeliveryCodeIssued(ParcelDeliveryCodeIssued event) {
        if (!enabled || sms.isEmpty() || event.receiverPhone() == null) {
            return;
        }
        // The DLT template to register: "SheOut: A parcel is on its way to you.
        // Give the partner this delivery code when she hands it over: {#var#}"
        String text = "SheOut: A parcel is on its way to you. Give the partner this delivery code when she hands it over: " + event.code();
        var result = sms.get().send(event.receiverPhone(), new OutboundMessage(text, text, null, null, OutboundMessage.Urgency.NORMAL, text));
        if (result.isFailure()) {
            log.warn("Receiver SMS for parcel {} not sent: {}", event.bookingId(), result.error());
        }
    }
}
