package com.sheout.payments.internal.tax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Everything about GST that is not code: whether it is on, SheOut's GSTIN,
 * and the rate and SAC code for each kind of supply. Off by default, and
 * every rate empty by default - none is written into this file, because the
 * rates, who the supplier is under Section 9(5), and the invoice format are
 * for SheOut's CA to sign off, and change if SheOut moves to a subscription
 * model (see the README).
 * <p>
 * Switched on half-configured, it refuses to start, naming what is missing:
 * an invoice with a guessed rate or no GSTIN is worse than none, because it
 * is a tax document somebody relies on.
 * <p>
 * Prices stay tax-inclusive. FARES_TAX_INCLUSIVE=false is refused rather
 * than built, because adding tax on top would change what riders are
 * charged, which this work does not do.
 */
@Component
public class TaxConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TaxConfiguration.class);
    /** 2 digits of state code, 10 of PAN, then entity number, Z, checksum. Shape only. */
    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private final boolean enabled;
    private final String gstin;
    private final String supplierName;
    private final String placeOfSupply;
    private final String invoicePrefix;
    private final Map<TaxCategory, BigDecimal> rates = new EnumMap<>(TaxCategory.class);
    private final Map<TaxCategory, String> sacCodes = new EnumMap<>(TaxCategory.class);

    public TaxConfiguration(
            @Value("${GST_ENABLED:false}") boolean enabled,
            @Value("${SHEOUT_GSTIN:}") String gstin,
            @Value("${SHEOUT_LEGAL_NAME:}") String supplierName,
            @Value("${FARES_TAX_INCLUSIVE:true}") boolean inclusive,
            // Where the service is supplied - every SheOut trip starts in
            // Telangana today. Intra-state, so the tax splits into CGST and SGST.
            @Value("${GST_PLACE_OF_SUPPLY:Telangana (36)}") String placeOfSupply,
            @Value("${GST_INVOICE_PREFIX:SO}") String invoicePrefix,
            @Value("${GST_RATE_BIKE:}") String rateBike,
            @Value("${GST_RATE_AUTO:}") String rateAuto,
            @Value("${GST_RATE_CAB:}") String rateCab,
            @Value("${GST_RATE_PARCEL:}") String rateParcel,
            @Value("${GST_RATE_PLATFORM_FEE:}") String ratePlatformFee,
            @Value("${GST_RATE_SELLER_LISTING_FEE:}") String rateListingFee,
            @Value("${GST_SAC_BIKE:}") String sacBike,
            @Value("${GST_SAC_AUTO:}") String sacAuto,
            @Value("${GST_SAC_CAB:}") String sacCab,
            @Value("${GST_SAC_PARCEL:}") String sacParcel,
            @Value("${GST_SAC_PLATFORM_FEE:}") String sacPlatformFee,
            @Value("${GST_SAC_SELLER_LISTING_FEE:}") String sacListingFee) {
        this.enabled = enabled;
        this.gstin = gstin == null ? "" : gstin.trim().toUpperCase();
        this.supplierName = supplierName == null ? "" : supplierName.trim();
        this.placeOfSupply = placeOfSupply;
        this.invoicePrefix = invoicePrefix;
        List<String> problems = new ArrayList<>();
        put(TaxCategory.BIKE, rateBike, sacBike, problems);
        put(TaxCategory.AUTO, rateAuto, sacAuto, problems);
        put(TaxCategory.CAB, rateCab, sacCab, problems);
        put(TaxCategory.PARCEL, rateParcel, sacParcel, problems);
        put(TaxCategory.PLATFORM_FEE, ratePlatformFee, sacPlatformFee, problems);
        put(TaxCategory.SELLER_LISTING_FEE, rateListingFee, sacListingFee, problems);
        if (!enabled) {
            log.info("GST is off: no tax is split out and no tax invoice is issued (GST_ENABLED=false)");
            return;
        }
        if (!GSTIN.matcher(this.gstin).matches()) {
            problems.add(0, "SHEOUT_GSTIN is missing or not a GSTIN");
        }
        if (this.supplierName.isEmpty()) {
            problems.add("SHEOUT_LEGAL_NAME is missing");
        }
        if (!inclusive) {
            problems.add("FARES_TAX_INCLUSIVE=false is not supported: tax on top would change what riders are charged");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("GST_ENABLED is true but GST is not fully configured: "
                    + String.join("; ", problems) + ". Every rate and SAC code must be signed off by SheOut's CA first.");
        }
        log.info("GST is on: GSTIN {}, rates {}", this.gstin, rates);
    }

    private void put(TaxCategory category, String rate, String sac, List<String> problems) {
        if (rate == null || rate.isBlank()) {
            problems.add("GST_RATE_" + category + " is missing");
        } else {
            try {
                BigDecimal value = new BigDecimal(rate.trim());
                if (value.signum() < 0 || value.compareTo(new BigDecimal("28")) > 0) {
                    problems.add("GST_RATE_" + category + " must be between 0 and 28, got " + rate);
                } else {
                    rates.put(category, value);
                }
            } catch (NumberFormatException ex) {
                problems.add("GST_RATE_" + category + " is not a number: " + rate);
            }
        }
        if (sac == null || sac.isBlank()) {
            problems.add("GST_SAC_" + category + " is missing");
        } else {
            sacCodes.put(category, sac.trim());
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String gstin() {
        return gstin;
    }

    public String supplierName() {
        return supplierName;
    }

    public String placeOfSupply() {
        return placeOfSupply;
    }

    public String invoicePrefix() {
        return invoicePrefix;
    }

    /** Only asked while enabled, when every rate is known to be set. */
    public BigDecimal rate(TaxCategory category) {
        return rates.get(category);
    }

    public String sacCode(TaxCategory category) {
        return sacCodes.get(category);
    }
}
