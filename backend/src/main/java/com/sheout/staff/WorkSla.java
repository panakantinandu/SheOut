package com.sheout.staff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * How long each kind of work may wait before it counts as late (sheout.staff.sla.*).
 * <p>
 * Targets, not rules: a late item is shown red on the console and counted on
 * the managers' dashboard. Nothing is refused, escalated automatically or
 * taken from anyone when one passes - those are a manager's decisions.
 */
@Component
public class WorkSla {

    private final Duration verification;
    private final Duration sosAck;
    private final Duration tripAlertAck;
    private final Duration sellerReview;
    private final Duration ticketHigh;
    private final Duration ticketMedium;
    private final Duration ticketLow;

    WorkSla(@Value("${sheout.staff.sla.verification-hours:24}") long verificationHours,
            @Value("${sheout.staff.sla.sos-ack-minutes:2}") long sosAckMinutes,
            @Value("${sheout.staff.sla.trip-alert-ack-minutes:10}") long tripAlertAckMinutes,
            @Value("${sheout.staff.sla.seller-review-hours:48}") long sellerReviewHours,
            @Value("${sheout.staff.sla.ticket-high-hours:4}") long ticketHighHours,
            @Value("${sheout.staff.sla.ticket-medium-hours:24}") long ticketMediumHours,
            @Value("${sheout.staff.sla.ticket-low-hours:72}") long ticketLowHours) {
        this.verification = Duration.ofHours(verificationHours);
        this.sosAck = Duration.ofMinutes(sosAckMinutes);
        this.tripAlertAck = Duration.ofMinutes(tripAlertAckMinutes);
        this.sellerReview = Duration.ofHours(sellerReviewHours);
        this.ticketHigh = Duration.ofHours(ticketHighHours);
        this.ticketMedium = Duration.ofHours(ticketMediumHours);
        this.ticketLow = Duration.ofHours(ticketLowHours);
    }

    public Duration verification() {
        return verification;
    }

    public Duration sosAck() {
        return sosAck;
    }

    public Duration tripAlertAck() {
        return tripAlertAck;
    }

    public Duration sellerReview() {
        return sellerReview;
    }

    /** By ticket priority name (HIGH, MEDIUM, LOW); anything else gets the MEDIUM target. */
    public Duration ticket(String priority) {
        return switch (priority == null ? "" : priority) {
            case "HIGH" -> ticketHigh;
            case "LOW" -> ticketLow;
            default -> ticketMedium;
        };
    }

    /** What the console needs to draw its clocks, in minutes. */
    public record Minutes(long verification, long sosAck, long tripAlertAck, long sellerReview,
                          long ticketHigh, long ticketMedium, long ticketLow) {
    }

    public Minutes minutes() {
        return new Minutes(verification.toMinutes(), sosAck.toMinutes(), tripAlertAck.toMinutes(), sellerReview.toMinutes(),
                ticketHigh.toMinutes(), ticketMedium.toMinutes(), ticketLow.toMinutes());
    }
}
