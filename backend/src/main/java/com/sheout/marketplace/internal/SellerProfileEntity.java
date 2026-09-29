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

    protected SellerProfileEntity() {
        // JPA
    }

    SellerProfileEntity(UUID accountId, String businessName, SellerCategory category, String contactPhone,
                        String whatsappNumber, String area, String websiteUrl) {
        this.accountId = accountId;
        this.status = SellerStatus.DRAFT;
        setDetails(businessName, category, contactPhone, whatsappNumber, area, websiteUrl);
    }

    /**
     * Her shop can be changed while it is hers to change: a draft, a
     * rejected application being put right, or a live shop. Not while a
     * person is reviewing it or it is awaiting payment - what was approved
     * is what she pays for - and not while suspended.
     */
    boolean editable() {
        return status == SellerStatus.DRAFT || status == SellerStatus.REJECTED || status == SellerStatus.ACTIVE;
    }

    boolean submittable() {
        return status == SellerStatus.DRAFT || status == SellerStatus.REJECTED;
    }

    void setDetails(String businessName, SellerCategory category, String contactPhone, String whatsappNumber,
                    String area, String websiteUrl) {
        this.websiteUrl = websiteUrl;
        this.businessName = businessName;
        this.category = category;
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

    void approve(UUID adminId, BigDecimal fee, Instant now) {
        status = SellerStatus.APPROVED_AWAITING_PAYMENT;
        reviewedAt = now;
        reviewedBy = adminId;
        listingFeeAmount = fee;
        rejectionReason = null;
    }

    void reject(UUID adminId, String reason, Instant now) {
        status = SellerStatus.REJECTED;
        reviewedAt = now;
        reviewedBy = adminId;
        rejectionReason = reason;
    }

    void activate(Instant now) {
        status = SellerStatus.ACTIVE;
        activatedAt = now;
    }

    void suspend(UUID adminId, String reason, Instant now) {
        status = SellerStatus.SUSPENDED;
        suspendedAt = now;
        reviewedBy = adminId;
        suspensionReason = reason;
    }

    void reinstate(UUID adminId, Instant now) {
        status = SellerStatus.ACTIVE;
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
}
