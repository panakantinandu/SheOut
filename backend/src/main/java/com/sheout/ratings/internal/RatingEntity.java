package com.sheout.ratings.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One person's entitlement to rate one trip, and what they said.
 * <p>
 * Account ids are plain UUID columns, not JPA relationships - no cross-module
 * foreign keys, same as every other module here.
 */
@Entity
@Table(name = "ratings")
public class RatingEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID raterAccountId;

    @Column(nullable = false)
    private UUID ratedAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole raterRole;

    /** Null while the slot is open. The only nullable field here that carries meaning. */
    private Integer stars;

    @Column(length = 500)
    private String comment;

    private Instant submittedAt;

    @Column(nullable = false)
    private Instant rateableUntil;

    protected RatingEntity() {
        // JPA
    }

    /** Created empty when the trip completes - see BookingCompletedListener. */
    public RatingEntity(UUID bookingId, UUID raterAccountId, UUID ratedAccountId,
                        AccountRole raterRole, Instant rateableUntil) {
        this.bookingId = bookingId;
        this.raterAccountId = raterAccountId;
        this.ratedAccountId = ratedAccountId;
        this.raterRole = raterRole;
        this.rateableUntil = rateableUntil;
    }

    /**
     * Fills the slot in. There is no setter for stars, and no way to change
     * one once set - a rating is what somebody thought at the time, and a
     * record that can be revised is a record that can be leaned on.
     */
    public void submit(int stars, String comment, Instant now) {
        this.stars = stars;
        this.comment = comment;
        this.submittedAt = now;
    }

    public boolean isSubmitted() {
        return stars != null;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getRaterAccountId() {
        return raterAccountId;
    }

    public UUID getRatedAccountId() {
        return ratedAccountId;
    }

    public AccountRole getRaterRole() {
        return raterRole;
    }

    public Integer getStars() {
        return stars;
    }

    public String getComment() {
        return comment;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getRateableUntil() {
        return rateableUntil;
    }
}
