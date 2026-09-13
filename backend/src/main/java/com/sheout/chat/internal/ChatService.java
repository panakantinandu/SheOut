package com.sheout.chat.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
import com.sheout.booking.BookingStatus;
import com.sheout.chat.ChatApi;
import com.sheout.chat.ChatError;
import com.sheout.chat.ChatMessage;
import com.sheout.sharedkernel.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Every rule about who may say what, and until when.
 * <p>
 * All of it lives here rather than in the controller, and none of it lives
 * in the apps. Hiding the input box once a trip ends is a courtesy to the
 * person looking at the screen; it is not a rule. Anyone can post to the
 * endpoint directly, so the closed thread has to be closed on this side.
 */
@Service
public class ChatService implements ChatApi {

    /**
     * Chat opens when a partner accepts and closes the moment the trip is
     * over.
     * <p>
     * Not from MATCHED: a booking is MATCHED the instant dispatch assigns
     * someone, before that partner has agreed to anything, and a rider
     * messaging a partner who has not accepted yet is writing to somebody
     * who may never arrive. ACCEPTED is the first point at which there are
     * genuinely two people on this trip.
     */
    private static final Set<BookingStatus> WRITABLE = Set.of(BookingStatus.ACCEPTED, BookingStatus.IN_PROGRESS);

    private static final int MAX_BODY = 1000;

    /**
     * Catches a phone number however it is spaced out or broken up: seven or
     * more digits once separators are stripped, which is short enough to
     * catch a local number and long enough not to trip on a flat number, a
     * house number, or a time.
     * <p>
     * Deliberately applied to the message with separators removed, because
     * "9 8 7 6 5 4 3 2 1 0" and "9876-543-210" are the same disclosure as
     * "9876543210" and a pattern that only matched the last one would be
     * theatre.
     */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()+_/\\\\|,:;*#\\[\\]]");
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{7,}");

    private final ChatMessageRepository repository;
    private final BookingApi bookingApi;

    ChatService(ChatMessageRepository repository, BookingApi bookingApi) {
        this.repository = repository;
        this.bookingApi = bookingApi;
    }

    /**
     * The thread for a booking, for one of the two people on it.
     * <p>
     * Readable in every status, including cancelled and completed. That is
     * the point of keeping it: when a rider and a partner disagree about
     * what was agreed, the thread is the only account of it either of them
     * has.
     */
    public Result<List<ChatMessage>, ChatError> threadFor(UUID bookingId, UUID requesterId) {
        Result<BookingParticipants, ChatError> participants = participantsFor(bookingId, requesterId);
        if (participants.isFailure()) {
            return Result.failure(participants.error());
        }
        return Result.success(repository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(ChatService::toMessage)
                .toList());
    }

    /** True when this booking can still be written to. Lets a client render an honest read-only state. */
    public Result<Boolean, ChatError> isOpen(UUID bookingId, UUID requesterId) {
        Result<BookingParticipants, ChatError> participants = participantsFor(bookingId, requesterId);
        if (participants.isFailure()) {
            return Result.failure(participants.error());
        }
        return Result.success(WRITABLE.contains(participants.value().status()));
    }

    @Transactional
    public Result<ChatMessage, ChatError> send(UUID bookingId, UUID senderId, AccountRole senderRole, String body) {
        Result<BookingParticipants, ChatError> participants = participantsFor(bookingId, senderId);
        if (participants.isFailure()) {
            return Result.failure(participants.error());
        }
        if (!WRITABLE.contains(participants.value().status())) {
            return Result.failure(ChatError.CHAT_CLOSED);
        }

        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_BODY) {
            return Result.failure(ChatError.INVALID_MESSAGE);
        }
        if (looksLikeAPhoneNumber(trimmed)) {
            return Result.failure(ChatError.CONTACT_DETAILS_NOT_ALLOWED);
        }

        ChatMessageEntity saved = repository.save(
                new ChatMessageEntity(bookingId, senderId, senderRole, trimmed));
        return Result.success(toMessage(saved));
    }

    @Override
    public List<ChatMessage> findThreadForAdmin(UUID bookingId) {
        return repository.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
                .map(ChatService::toMessage)
                .toList();
    }

    /**
     * Refuses a message carrying what looks like a phone number.
     * <p>
     * REFUSED, not silently redacted, and that is a deliberate choice. This
     * thread is kept as the record of a trip that went wrong, and quietly
     * rewriting what somebody typed would make that record a forgery. A
     * refusal with a reason also teaches the rule, where a redaction leaves
     * the sender believing their number went through.
     * <p>
     * This is the other half of taking direct calling away. Removing the
     * Call button while leaving a text box people can paste a number into
     * changes nothing about the disclosure; it just adds a step.
     * <p>
     * It will occasionally refuse something innocent - a long flat number,
     * an order reference. That trade is taken knowingly, and the message
     * says what to do instead.
     */
    static boolean looksLikeAPhoneNumber(String body) {
        String condensed = SEPARATORS.matcher(body).replaceAll("");
        return LONG_DIGIT_RUN.matcher(condensed).find();
    }

    /**
     * Resolves the booking and checks the caller is on it.
     * <p>
     * A caller who is not a participant gets the same BOOKING_NOT_FOUND as
     * one asking about a booking that does not exist, so booking ids cannot
     * be probed for existence by anyone with an account. The same reasoning
     * BookingController.requireParticipant records for its 404s.
     */
    private Result<BookingParticipants, ChatError> participantsFor(UUID bookingId, UUID requesterId) {
        Result<BookingParticipants, BookingError> result = bookingApi.getParticipants(bookingId);
        if (result.isFailure()) {
            return Result.failure(ChatError.BOOKING_NOT_FOUND);
        }
        BookingParticipants participants = result.value();
        boolean onThisTrip = requesterId.equals(participants.customerId())
                || requesterId.equals(participants.driverId());
        if (!onThisTrip) {
            return Result.failure(ChatError.BOOKING_NOT_FOUND);
        }
        return Result.success(participants);
    }

    private static ChatMessage toMessage(ChatMessageEntity entity) {
        return new ChatMessage(
                entity.getId(),
                entity.getBookingId(),
                entity.getSenderAccountId(),
                entity.getSenderRole(),
                entity.getBody(),
                entity.getCreatedAt());
    }
}
