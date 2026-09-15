package com.sheout.users.internal;

import com.sheout.sharedkernel.BaseEntity;
import com.sheout.users.TrustStats;
import com.sheout.users.OnlineStatus;
import com.sheout.users.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "driver_profiles")
public class DriverProfileEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID accountId;

    @Column(length = 150)
    private String name;

    /** Required to complete a profile, and 18 or over - see ProfileRules. Null only for accounts from before V20. */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Optional contact address: receipts, and support replies on a device without push. */
    @Column(length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VehicleType vehicleType;

    @Column(length = 20)
    private String vehicleRegistrationNumber;

    /**
     * Storage key for the photo a rider sees, not a URL - see V12. Null on
     * accounts that predate the requirement, which both apps render as a
     * silhouette rather than a broken image.
     */
    @Column(length = 500)
    private String profilePhotoKey;

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

    /**
     * What this account's ratings come to.
     * <p>
     * A projection maintained from RatingSubmitted, the same way the
     * cancellation counters beside it are maintained from booking events.
     * The ratings table stays the source of truth; these are here so one row
     * carries every number the trust check needs to make a single decision.
     * <p>
     * Null average means nobody has rated this account yet, which is not the
     * same as a bad score - see TrustStats.
     */
    @Column(precision = 3, scale = 2)
    private BigDecimal averageStars;

    @Column(nullable = false)
    private int totalRatings = 0;

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

    public String getProfilePhotoKey() {
        return profilePhotoKey;
    }

    public void setProfilePhotoKey(String profilePhotoKey) {
        this.profilePhotoKey = profilePhotoKey;
    }

    /** Whether this partner has the photo a rider is entitled to see before getting in. */
    public boolean hasProfilePhoto() {
        return profilePhotoKey != null && !profilePhotoKey.isBlank();
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

    /**
     * Every trust number this account carries, in one record, because there
     * is one flag and it is decided from all of them together.
     */
    public TrustStats getTrustStats() {
        return new TrustStats(
                totalBookings,
                totalCancellations,
                averageStars == null ? null : averageStars.doubleValue(),
                totalRatings,
                flaggedAt != null,
                flaggedAt,
                flaggedReason);
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
    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /** Everything the completion screen asks for is on file. */
    public boolean isProfileComplete() {
        return name != null && !name.isBlank() && dateOfBirth != null && hasProfilePhoto();
    }

    public void clearReviewFlag() {
        this.flaggedAt = null;
        this.flaggedReason = null;
    }

    public BigDecimal getAverageStars() {
        return averageStars;
    }

    public int getTotalRatings() {
        return totalRatings;
    }

    /**
     * Takes the figures the ratings module just calculated, rather than
     * recomputing them here. Two modules independently averaging the same
     * rows is how they end up disagreeing about somebody's score.
     */
    public void recordRatingAggregate(double averageStars, int totalRatings) {
        this.averageStars = BigDecimal.valueOf(averageStars).setScale(2, RoundingMode.HALF_UP);
        this.totalRatings = totalRatings;
    }
}
