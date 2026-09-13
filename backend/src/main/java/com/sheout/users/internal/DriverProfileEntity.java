package com.sheout.users.internal;

import com.sheout.sharedkernel.BaseEntity;
import com.sheout.users.CancellationStats;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_profiles")
public class DriverProfileEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID accountId;

    @Column(length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VehicleType vehicleType;

    @Column(length = 20)
    private String vehicleRegistrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OnlineStatus onlineStatus = OnlineStatus.OFFLINE;

    @Column(nullable = false)
    private boolean verified = false;

    /**
     * How many bookings this account has been party to, and how many of them
     * it cancelled.
     * <p>
     * A projection maintained by UserProfileEventListeners, the same way
     * {@code verified} is. The bookings table stays the source of truth; if
     * these two ever drift they can be rebuilt from it.
     */
    @Column(nullable = false)
    private int totalBookings = 0;

    @Column(nullable = false)
    private int totalCancellations = 0;

    /**
     * Non-null once the cancellation rate crossed the configured threshold.
     * A flag for a person to look at - it takes nothing away by itself.
     */
    private Instant flaggedAt;

    @Column(length = 500)
    private String flaggedReason;

    protected DriverProfileEntity() {
        // JPA
    }

    /** Created empty on AccountRegistered - vehicle details are filled in later by the client. */
    public DriverProfileEntity(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleRegistrationNumber() {
        return vehicleRegistrationNumber;
    }

    public void setVehicleRegistrationNumber(String vehicleRegistrationNumber) {
        this.vehicleRegistrationNumber = vehicleRegistrationNumber;
    }

    public OnlineStatus getOnlineStatus() {
        return onlineStatus;
    }

    public void setOnlineStatus(OnlineStatus onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public int getTotalBookings() {
        return totalBookings;
    }

    public int getTotalCancellations() {
        return totalCancellations;
    }

    public Instant getFlaggedAt() {
        return flaggedAt;
    }

    public String getFlaggedReason() {
        return flaggedReason;
    }

    public CancellationStats getCancellationStats() {
        return new CancellationStats(totalBookings, totalCancellations, flaggedAt != null, flaggedAt, flaggedReason);
    }

    /** One more booking in the denominator. */
    public void recordBooking() {
        totalBookings++;
    }

    /** One more cancellation, already attributed to this account by the caller. */
    public void recordCancellation() {
        totalCancellations++;
    }

    /**
     * Puts this account in front of an operator, with the numbers that got
     * it there.
     * <p>
     * The timestamp is only set the first time, so it keeps meaning "when
     * this account became worth reviewing" rather than "when it last
     * cancelled". The reason is refreshed each time so an operator reading
     * the queue sees the current figures, not the ones from the crossing.
     */
    public void flagForReview(String reason) {
        if (flaggedAt == null) {
            this.flaggedAt = Instant.now();
        }
        this.flaggedReason = reason;
    }

    /** An operator has looked and is satisfied. */
    public void clearReviewFlag() {
        this.flaggedAt = null;
        this.flaggedReason = null;
    }
}
