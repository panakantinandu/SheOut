package com.sheout.marketplace.internal;

import com.sheout.marketplace.SellerCategory;
import com.sheout.marketplace.SellerStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A seller's shop, attached to her rider account. Status moves only through
 * the methods here, each of which checks where it is coming from - see
 * SellerStatus for the whole path.
 */
@Entity
@Table(name = "seller_profiles")
public class SellerProfileEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID accountId;

    @Column(nullable = false, length = 80)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SellerCategory category;

    /** For OTHER: what she sells, in her words. */
    @Column(length = 40)
    private String customCategory;

    @Column(nullable = false, length = 15)
    private String contactPhone;

    @Column(length = 15)
    private String whatsappNumber;

    /** Where she works from, as she writes it - a locality, not coordinates. Null when she has not said. */
    @Column(length = 80)
    private String area;

    /** Her own website, an http(s) address checked by WebsiteAddress. Null when she has none. */
    @Column(length = 200)
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SellerStatus status;

    @Column(length = 500)
    private String rejectionReason;

    @Column(length = 500)
    private String suspensionReason;

    private Instant submittedAt;
    private Instant reviewedAt;
    private UUID reviewedBy;

    @Column(precision = 10, scale = 2)
    private BigDecimal listingFeeAmount;

    private Instant activatedAt;
    private Instant suspendedAt;
    private Instant editedLiveAt;

    /** Listed until this moment (plus grace) - see SellerPlan. Null until approved. */
    private Instant paidUntil;
    /** The end of her free first month(s). */
    private Instant freeUntil;
    /** Months paid for; the next payment is for paidPeriods + 1. */
    private int paidPeriods;
    /** The last plan reminder sent for the current month (see SellerPlanReminders); cleared when she pays. */
    @Column(length = 20)
    private String lastPlanNotice;

    /** An example shop with nobody behind it - see SampleShops. */
    private boolean sample;

    /**
     * Where a parcel for one of her orders is collected - her choice, set on
     * a map (home, shop, or a gate she meets the partner at). Null until she
     * sets one; given to a buyer only when the buyer books a parcel.
     */
    @Column(name = "parcel_pickup_label", length = 200)
    private String parcelPickupLabel;
    @Column(name = "parcel_pickup_lat")
    private Double parcelPickupLat;
    @Column(name = "parcel_pickup_lng")
    private Double parcelPickupLng;

    protected SellerProfileEntity() {
        // JPA
    }

    SellerProfileEntity(UUID accountId, String businessName, SellerCategory category, String customCategory,
                        String contactPhone, String whatsappNumber, String area, String websiteUrl) {
        this.accountId = accountId;
        this.status = SellerStatus.DRAFT;
        setDetails(businessName, category, customCategory, contactPhone, whatsappNumber, area, websiteUrl);
    }

    /**
     * Her shop can be changed while it is hers to change: a draft, a
     * rejected application being put right, a live shop, or one hidden
     * until she pays (so it is ready when she does). Not while a person is
     * reviewing it, and not while suspended.
     */
    boolean editable() {
        return status == SellerStatus.DRAFT || status == SellerStatus.REJECTED || status == SellerStatus.ACTIVE
                || status == SellerStatus.LAPSED;
    }

    boolean submittable() {
        return status == SellerStatus.DRAFT || status == SellerStatus.REJECTED;
    }

    void setDetails(String businessName, SellerCategory category, String customCategory, String contactPhone,
                    String whatsappNumber, String area, String websiteUrl) {
        this.websiteUrl = websiteUrl;
        this.businessName = businessName;
        this.category = category;
        this.customCategory = customCategory;
        this.contactPhone = contactPhone;
        this.whatsappNumber = whatsappNumber;
        this.area = area;
    }

    /** A live shop changed after it was approved - noted for operations, not re-reviewed. */
    void touchedWhileLive(Instant now) {
        if (status == SellerStatus.ACTIVE) {
            editedLiveAt = now;
        }
    }

    void submit(Instant now) {
        status = SellerStatus.SUBMITTED_FOR_REVIEW;
        submittedAt = now;
    }

    /**
     * Approved: live at once, free for freeMonths from now. A shop that has
     * had its free months already keeps the dates it has.
     */
    void approve(UUID adminId, BigDecimal monthlyFee, int freeMonths, Instant now) {
        status = SellerStatus.ACTIVE;
        reviewedAt = now;
        reviewedBy = adminId;
        listingFeeAmount = monthlyFee;
        rejectionReason = null;
        activatedAt = now;
        if (paidUntil == null) {
            paidUntil = plusMonths(now, freeMonths);
            freeUntil = paidUntil;
        }
        lastPlanNotice = null;
    }

    /**
     * Month {@code period} was paid for. A month is added to whichever is
     * later, now or the end of her current month, and a hidden shop is back.
     * False when this is not the month she owes next (a repeat event).
     */
    boolean paidFor(int period, BigDecimal amount, Instant now) {
        if (period != paidPeriods + 1) {
            return false;
        }
        Instant from = paidUntil == null || paidUntil.isBefore(now) ? now : paidUntil;
        paidUntil = plusMonths(from, 1);
        paidPeriods = period;
        listingFeeAmount = amount;
        lastPlanNotice = null;
        if (status == SellerStatus.LAPSED) {
            status = SellerStatus.ACTIVE;
        }
        return true;
    }

    /**
     * An example shop: live at once and never due - it has no plan and is
     * never reminded or hidden; it goes when sample shops are turned off.
     */
    void makeSample(Instant now) {
        sample = true;
        status = SellerStatus.ACTIVE;
        submittedAt = now;
        reviewedAt = now;
        activatedAt = now;
        paidUntil = null;
        freeUntil = null;
    }

    /** Her month and its grace days are over: hidden until she pays. */
    void lapse() {
        status = SellerStatus.LAPSED;
    }

    void setParcelPickup(String label, Double lat, Double lng) {
        this.parcelPickupLabel = label;
        this.parcelPickupLat = lat;
        this.parcelPickupLng = lng;
    }

    void planNoticeSent(String notice) {
        lastPlanNotice = notice;
    }

    private static Instant plusMonths(Instant from, int months) {
        return from.atZone(java.time.ZoneId.of("Asia/Kolkata")).plusMonths(months).toInstant();
    }

    void reject(UUID adminId, String reason, Instant now) {
        status = SellerStatus.REJECTED;
        reviewedAt = now;
        reviewedBy = adminId;
        rejectionReason = reason;
    }

    void suspend(UUID adminId, String reason, Instant now) {
        status = SellerStatus.SUSPENDED;
        suspendedAt = now;
        reviewedBy = adminId;
        suspensionReason = reason;
    }

    /** Back from a suspension: live if her month is still running, otherwise hidden until she pays. */
    void reinstate(UUID adminId, Instant now, java.time.Duration grace) {
        status = paidUntil != null && now.isBefore(paidUntil.plus(grace)) ? SellerStatus.ACTIVE : SellerStatus.LAPSED;
        reviewedBy = adminId;
        reviewedAt = now;
        suspensionReason = null;
    }

    /** Her account is being deleted: out of the directory for good, and nothing that reaches her kept. */
    void forget(Instant now) {
        status = SellerStatus.SUSPENDED;
        suspendedAt = now;
        suspensionReason = "Account deleted";
        businessName = "[deleted]";
        contactPhone = "";
        whatsappNumber = null;
        area = null;
        websiteUrl = null;
    }

    public UUID getAccountId() { return accountId; }
    public String getBusinessName() { return businessName; }
    public SellerCategory getCategory() { return category; }
    public String getCustomCategory() { return customCategory; }
    public String getContactPhone() { return contactPhone; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public String getArea() { return area; }
    public String getWebsiteUrl() { return websiteUrl; }
    public SellerStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public String getSuspensionReason() { return suspensionReason; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public BigDecimal getListingFeeAmount() { return listingFeeAmount; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getEditedLiveAt() { return editedLiveAt; }
    public Instant getPaidUntil() { return paidUntil; }
    public Instant getFreeUntil() { return freeUntil; }
    public int getPaidPeriods() { return paidPeriods; }
    public String getLastPlanNotice() { return lastPlanNotice; }
    public boolean isSample() { return sample; }
    public String getParcelPickupLabel() { return parcelPickupLabel; }
    public Double getParcelPickupLat() { return parcelPickupLat; }
    public Double getParcelPickupLng() { return parcelPickupLng; }
}
