package com.sheout.payments.internal.tax;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * GST on a captured payment: the inclusive price split into taxable value
 * and tax, and the tax invoice, numbered without gaps per financial year.
 * <p>
 * With GST off (the default) this does nothing at all - no split, no number
 * taken, no invoice. The price the rider pays is never changed here either
 * way: the tax is found inside it, not added to it.
 * <p>
 * Called inside the capturing transaction (MANDATORY): the number is taken
 * under a lock on the year's counter and committed with the capture, so a
 * capture that fails leaves no hole in the sequence.
 */
@Service
public class GstService {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** The taxable value and tax inside an inclusive amount, and the halves an intra-state supply is split into. */
    public record Split(BigDecimal taxableValue, BigDecimal tax, BigDecimal cgst, BigDecimal sgst) {
    }

    private final TaxConfiguration config;
    private final TaxInvoiceRepository invoices;
    private final Clock clock;

    @Autowired
    public GstService(TaxConfiguration config, TaxInvoiceRepository invoices) {
        this(config, invoices, Clock.systemUTC());
    }

    GstService(TaxConfiguration config, TaxInvoiceRepository invoices, Clock clock) {
        this.config = config;
        this.invoices = invoices;
        this.clock = clock;
    }

    public boolean enabled() {
        return config.enabled();
    }

    /**
     * amount includes tax at ratePercent: taxable = amount × 100 / (100 + rate),
     * to the paisa; the tax is the rest, so the two always add back to exactly
     * what she paid. The tax is halved into CGST and SGST, the odd paisa
     * going to SGST.
     */
    public static Split splitInclusive(BigDecimal amount, BigDecimal ratePercent) {
        BigDecimal paid = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxable = paid.multiply(HUNDRED).divide(HUNDRED.add(ratePercent), 2, RoundingMode.HALF_UP);
        BigDecimal tax = paid.subtract(taxable);
        BigDecimal cgst = tax.divide(new BigDecimal("2"), 2, RoundingMode.DOWN);
        return new Split(taxable, tax, cgst, tax.subtract(cgst));
    }

    /** "2026-27" for any day from 1 April 2026 to 31 March 2027, in India. */
    public static String financialYear(LocalDate day) {
        int start = day.getMonthValue() >= 4 ? day.getYear() : day.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }

    /** What a capture records on its payment and its invoice - empty when GST is off. */
    public record Applied(Split split, BigDecimal ratePercent, TaxCategory category, TaxInvoiceEntity invoice) {
    }

    /**
     * Splits the tax out of what she paid and issues the invoice, once per
     * payment. Empty with GST off, and for a payment of nothing (a trip a
     * promotion paid in full) - see the README on what the CA must decide
     * about promotions.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Applied> applyOnCapture(UUID paymentId, UUID bookingId, BigDecimal amountPaid, TaxCategory category,
                                            UUID recipientAccountId, String recipientName, String description) {
        if (!config.enabled() || amountPaid == null || amountPaid.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal rate = config.rate(category);
        Split split = splitInclusive(amountPaid, rate);
        Optional<TaxInvoiceEntity> existing = invoices.findByPaymentId(paymentId);
        if (existing.isPresent()) {
            return Optional.of(new Applied(split, rate, category, existing.get()));
        }
        Instant now = clock.instant();
        String fy = financialYear(LocalDate.ofInstant(now, INDIA));
        invoices.ensureSequence(fy);
        long number = invoices.lockNext(fy);
        invoices.setNext(fy, number + 1);
        String invoiceNumber = config.invoicePrefix() + "/" + fy + "/" + String.format("%06d", number);
        TaxInvoiceEntity invoice = invoices.save(new TaxInvoiceEntity(invoiceNumber, fy, number, paymentId, bookingId, now,
                config.gstin(), config.supplierName(), config.placeOfSupply(), config.sacCode(category), description,
                recipientAccountId, recipientName == null ? null : truncate(recipientName, 150), split.taxableValue(), rate,
                split.cgst(), split.sgst(), amountPaid.setScale(2, RoundingMode.HALF_UP)));
        return Optional.of(new Applied(split, rate, category, invoice));
    }

    @Transactional(readOnly = true)
    public Optional<TaxInvoiceEntity> invoiceForBooking(UUID bookingId) {
        return invoices.findFirstByBookingId(bookingId);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
