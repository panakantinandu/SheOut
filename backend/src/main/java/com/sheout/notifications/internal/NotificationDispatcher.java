package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.notifications.internal.channel.NotificationChannel;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.sharedkernel.Result;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.CustomerProfileSummary;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns "tell this account this" into an inbox entry plus whatever deliveries
 * its DeliveryPolicy calls for, recording every attempt.
 * <p>
 * The only class that decides which channels a notification goes through.
 * The listeners decide what to say; the channels decide how to send it.
 */
@Component
public class NotificationDispatcher {

    private final Map<NotificationChannelType, NotificationChannel> channels = new EnumMap<>(NotificationChannelType.class);
    private final NotificationLogService log;
    private final PushDeviceService pushDevices;
    private final AuthApi authApi;
    private final CustomerProfileApi customerProfileApi;
    private final DriverProfileApi driverProfileApi;

    public NotificationDispatcher(List<NotificationChannel> channelBeans, NotificationLogService log,
                                  PushDeviceService pushDevices, AuthApi authApi,
                                  CustomerProfileApi customerProfileApi, DriverProfileApi driverProfileApi) {
        channelBeans.forEach(channel -> channels.put(channel.type(), channel));
        this.log = log;
        this.pushDevices = pushDevices;
        this.authApi = authApi;
        this.customerProfileApi = customerProfileApi;
        this.driverProfileApi = driverProfileApi;
    }

    /** Records the notification in the account's inbox and delivers it by its type's policy. */
    public void deliver(UUID accountId, NotificationType type, OutboundMessage message) {
        DeliveryPolicy policy = DeliveryPolicy.forType(type);
        UUID notificationId = log.recordNotification(accountId, type, message);

        boolean reached = false;
        if (policy.push()) {
            reached = pushToDevices(notificationId, pushDevices.devicesFor(accountId), message);
        }

        Optional<AccountSummary> account = Optional.empty();
        if (policy.emailAlways() || (!reached && policy.emailFallback())) {
            account = authApi.findAccount(accountId);
            String email = account.map(this::contactEmail).orElse(null);
            if (email != null) {
                Result<Void, SendFailure> outcome = send(NotificationChannelType.EMAIL, email, message);
                log.recordDelivery(notificationId, NotificationChannelType.EMAIL, email, outcome);
                reached = reached || outcome.isSuccess();
            }
        }

        if (!reached && policy.smsFallback()) {
            if (account.isEmpty()) {
                account = authApi.findAccount(accountId);
            }
            String phone = account.map(AccountSummary::phoneNumber).orElse(null);
            if (phone == null) {
                log.recordDelivery(notificationId, NotificationChannelType.SMS, null,
                        Result.failure(SendFailure.of(NotificationError.NO_RECIPIENT_ADDRESS)));
            } else {
                log.recordDelivery(notificationId, NotificationChannelType.SMS, phone,
                        send(NotificationChannelType.SMS, phone, message));
            }
        }
    }

    /**
     * Every account in a role that has a device registered - operators for an
     * SOS. An operator without push still has the console's sidebar count, so
     * accounts with no device get no inbox entry either: they have no inbox.
     */
    public void deliverToRole(AccountRole role, NotificationType type, OutboundMessage message) {
        Map<UUID, List<PushDeviceEntity>> byAccount = pushDevices.devicesForRole(role).stream()
                .collect(Collectors.groupingBy(PushDeviceEntity::getAccountId));
        byAccount.forEach((accountId, devices) -> {
            UUID notificationId = log.recordNotification(accountId, type, message);
            pushToDevices(notificationId, devices, message);
        });
    }

    private boolean pushToDevices(UUID notificationId, List<PushDeviceEntity> devices, OutboundMessage message) {
        boolean reached = false;
        for (PushDeviceEntity device : devices) {
            Result<Void, SendFailure> outcome = send(NotificationChannelType.PUSH, device.getToken(), message);
            // The token is a credential for putting text on her lock screen;
            // the audit trail keeps only enough of it to tell devices apart.
            log.recordDelivery(notificationId, NotificationChannelType.PUSH, tokenHint(device.getToken()), outcome);
            if (outcome.isFailure() && outcome.error().error() == NotificationError.RECIPIENT_GONE) {
                pushDevices.forget(device.getToken());
            }
            reached = reached || outcome.isSuccess();
        }
        return reached;
    }

    /**
     * One email, through the same channel every other email uses. For the
     * announcement mailing, which needs the outcome per address rather than
     * the whole deliver() flow - a broadcast has no inbox row per recipient
     * and no fallback to SMS.
     */
    Result<Void, SendFailure> sendEmail(String address, OutboundMessage message) {
        return send(NotificationChannelType.EMAIL, address, message);
    }

    private Result<Void, SendFailure> send(NotificationChannelType type, String address, OutboundMessage message) {
        NotificationChannel channel = channels.get(type);
        if (channel == null) {
            return Result.failure(SendFailure.of(NotificationError.NOT_CONFIGURED, "No " + type + " channel"));
        }
        return channel.send(address, message);
    }

    /** The email on her profile; for an account that signed in with Google and set none, that address. */
    private String contactEmail(AccountSummary account) {
        String profileEmail = switch (account.role()) {
            case CUSTOMER -> customerProfileApi.findByAccountId(account.id()).map(CustomerProfileSummary::email).orElse(null);
            case DRIVER -> driverProfileApi.findByAccountId(account.id()).map(DriverProfileSummary::email).orElse(null);
            default -> null;
        };
        String email = profileEmail != null && !profileEmail.isBlank() ? profileEmail : account.email();
        return email == null || email.isBlank() ? null : email;
    }

    static String tokenHint(String token) {
        return token.length() <= 8 ? "push:" + token : "push:…" + token.substring(token.length() - 8);
    }
}
