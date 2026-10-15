package com.sheout.notifications;

/**
 * Why a broadcast is sent - which decides whether it may go to everybody.
 * <p>
 * A SERVICE notice is something she needs to use SheOut safely or at all: a
 * safety advisory, a change to the service area or hours, an outage. A
 * PROMOTIONAL message is news, an offer, a "come back" - something she has
 * to have agreed to receive. Until there is an opt-in to record that
 * agreement, promotional broadcasts are refused (PROMOTIONAL_MESSAGES_ENABLED,
 * off by default).
 */
public enum AnnouncementPurpose {
    SERVICE,
    PROMOTIONAL
}
