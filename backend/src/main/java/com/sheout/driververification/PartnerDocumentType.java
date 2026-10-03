package com.sheout.driververification;

/**
 * The documents a partner's right to carry somebody rests on, besides her
 * identity.
 * <p>
 * {@code expires} says whether the document carries a printed "valid until"
 * an operator must record before approving it. The police certificate does
 * not: it is re-verified on a period instead (POLICE_REVERIFY_MONTHS), and a
 * background report is evidence, not a licence.
 * <p>
 * {@code partnerUploads} is false only for the background report, which
 * comes from SheOut's operators or a verification provider, never from her.
 * <p>
 * {@code numbered} says the document number is required to approve it - an
 * operator who approves a licence without typing its number has not read it.
 * <p>
 * Stored by name; never rename a value while rows carry it.
 */
public enum PartnerDocumentType {
    DRIVING_LICENCE(true, true, true),
    VEHICLE_RC(true, true, true),
    VEHICLE_INSURANCE(true, true, true),
    PUC(true, true, true),
    FITNESS_CERTIFICATE(true, true, true),
    POLICE_CERTIFICATE(false, true, true),
    BACKGROUND_CHECK_REPORT(false, false, false);

    private final boolean expires;
    private final boolean partnerUploads;
    private final boolean numbered;

    PartnerDocumentType(boolean expires, boolean partnerUploads, boolean numbered) {
        this.expires = expires;
        this.partnerUploads = partnerUploads;
        this.numbered = numbered;
    }

    public boolean expires() {
        return expires;
    }

    public boolean partnerUploads() {
        return partnerUploads;
    }

    public boolean numbered() {
        return numbered;
    }

    /** The evidence behind her police check, rather than a document about her vehicle or licence. */
    public boolean policeEvidence() {
        return this == POLICE_CERTIFICATE || this == BACKGROUND_CHECK_REPORT;
    }

    /** Said in an operator's or a partner's words, for messages built on the server. */
    public String plainName() {
        return switch (this) {
            case DRIVING_LICENCE -> "driving licence";
            case VEHICLE_RC -> "vehicle RC";
            case VEHICLE_INSURANCE -> "vehicle insurance";
            case PUC -> "PUC certificate";
            case FITNESS_CERTIFICATE -> "fitness certificate";
            case POLICE_CERTIFICATE -> "police certificate";
            case BACKGROUND_CHECK_REPORT -> "background check report";
        };
    }
}
