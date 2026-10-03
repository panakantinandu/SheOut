package com.sheout.payments.internal.tax;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One tax invoice, as issued: every fact on it frozen at the moment of
 * capture, so a later change of rate, GSTIN or her name cannot rewrite a
 * document she has already been given. See V59__gst_and_tax_invoices.sql.
 */
@Entity
@Table(name = "tax_invoices")
public class TaxInvoiceEntity extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String invoiceNumber;
    @Column(nullable = false, length = 7)
    private String financialYear;
    @Column(nullable = false)
    private long sequenceNumber;
    @Column(nullable = false, unique = true)
    private UUID paymentId;
    private UUID bookingId;
    @Column(nullable = false)
    private Instant issuedAt;
    @Column(nullable = false, length = 15)
    private String supplierGstin;
    @Column(length = 200)
    private String supplierName;
    @Column(nullable = false, length = 60)
    private String placeOfSupply;
    @Column(nullable = false, length = 10)
    private String sacCode;
    @Column(nullable = false, length = 200)
    private String description;
    private UUID recipientAccountId;
    @Column(length = 150)
    private String recipientName;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxableValue;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRatePercent;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal cgstAmount;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sgstAmount;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal igstAmount;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    protected TaxInvoiceEntity() {
        // JPA
    }

    @SuppressWarnings("java:S107")
    TaxInvoiceEntity(String invoiceNumber, String financialYear, long sequenceNumber, UUID paymentId, UUID bookingId,
                     Instant issuedAt, String supplierGstin, String supplierName, String placeOfSupply, String sacCode,
                     String description, UUID recipientAccountId, String recipientName, BigDecimal taxableValue,
                     BigDecimal taxRatePercent, BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal totalAmount) {
        this.invoiceNumber = invoiceNumber;
        this.financialYear = financialYear;
        this.sequenceNumber = sequenceNumber;
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.issuedAt = issuedAt;
        this.supplierGstin = supplierGstin;
        this.supplierName = supplierName;
        this.placeOfSupply = placeOfSupply;
        this.sacCode = sacCode;
        this.description = description;
        this.recipientAccountId = recipientAccountId;
        this.recipientName = recipientName;
        this.taxableValue = taxableValue;
        this.taxRatePercent = taxRatePercent;
        this.cgstAmount = cgstAmount;
        this.sgstAmount = sgstAmount;
        this.igstAmount = BigDecimal.ZERO.setScale(2);
        this.totalAmount = totalAmount;
    }

    public String getInvoiceNumber() { return invoiceNumber; }
    public String getFinancialYear() { return financialYear; }
    public long getSequenceNumber() { return sequenceNumber; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getBookingId() { return bookingId; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getSupplierGstin() { return supplierGstin; }
    public String getSupplierName() { return supplierName; }
    public String getPlaceOfSupply() { return placeOfSupply; }
    public String getSacCode() { return sacCode; }
    public String getDescription() { return description; }
    public UUID getRecipientAccountId() { return recipientAccountId; }
    public String getRecipientName() { return recipientName; }
    public BigDecimal getTaxableValue() { return taxableValue; }
    public BigDecimal getTaxRatePercent() { return taxRatePercent; }
    public BigDecimal getCgstAmount() { return cgstAmount; }
    public BigDecimal getSgstAmount() { return sgstAmount; }
    public BigDecimal getIgstAmount() { return igstAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
