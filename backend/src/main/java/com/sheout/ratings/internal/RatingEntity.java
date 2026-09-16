package com.sheout.ratings.internal;

import com.sheout.auth.AccountRole;
import com.sheout.ratings.RatingTag;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /**
     * The quick reasons tapped with the stars. Empty for most ratings.
     * <p>
     * Fetched eagerly, in batches: every read of a rating renders them, and
     * open-in-view is off here, so a lazy collection would simply fail
     * outside the transaction that loaded it. The batch size keeps a page of
     * twenty history rows to two queries instead of twenty-one.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rating_tags", joinColumns = @JoinColumn(name = "rating_id"))
    @Column(name = "tag", length = 40, nullable = false)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 100)
    private Set<RatingTag> tags = new LinkedHashSet<>();

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
    /**
     * Account deletion: the rater's written words go, their stars stay. The
     * stars are part of another person's average; the comment is only ever
     * this person's own text, and nothing requires keeping it.
     */
    void eraseComment() {
        this.comment = null;
    }

    public void submit(int stars, String comment, Collection<RatingTag> tags, Instant now) {
        this.stars = stars;
        this.comment = comment;
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
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

    public Set<RatingTag> getTags() {
        return tags;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getRateableUntil() {
        return rateableUntil;
    }
}
