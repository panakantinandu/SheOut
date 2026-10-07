package com.sheout.driververification;

import java.util.EnumSet;
import java.util.Set;

/**
 * Why a person sent her ID or a document back: one of a fixed list, each
 * with the plain message she is shown on how to fix it.
 * <p>
 * A list rather than free text so that every applicant turned away for the
 * same reason is told the same thing, in her own language (the apps
 * translate by code; {@link #message} is the English, kept on the record for
 * any older app that shows the stored text), and so it can be counted.
 * <p>
 * {@code remembersNumber} is true only for fraud (an altered or fake
 * document) and a confirmed mismatch (the government record says the
 * licence or vehicle is someone else's). Those numbers are kept, and fail
 * the check if they come back. Never for an expired document, a blurry
 * photo or a typing mistake: those are fixed by sending it again.
 * <p>
 * {@code {document}} in a message is the document's name ("licence", "RC").
 * Stored by name; never rename a value while rows carry it.
 */
public enum RejectionReason {

    // ---- her ID and selfie
    ID_UNREADABLE(Scope.IDENTITY, false,
            "Your ID is not readable. Take the photo again flat, in good light, with all four corners showing."),
    NOT_A_GOVERNMENT_ID(Scope.IDENTITY, false,
            "This is not a government photo ID. Send your Aadhaar, passport, voter ID or driving licence."),
    ID_NAME_MISMATCH(Scope.IDENTITY, false,
            "The name on your ID is not the name on your profile. Correct your name in Profile, or send an ID with your name on it."),
    SELFIE_MISMATCH(Scope.IDENTITY, false,
            "Your live selfie does not match the photo on your ID. Take the selfie again in good light, facing the camera."),
    UNDER_18(Scope.IDENTITY, false,
            "You must be 18 or older to use SheOut. If your date of birth is wrong, correct it in Profile and send your ID again."),
    WOMAN_NOT_CONFIRMED(Scope.IDENTITY, false,
            "We could not confirm from this ID that you are a woman. If you think this is a mistake, contact support."),
    ID_ALTERED(Scope.IDENTITY, false,
            "We could not accept this ID. If you think this is a mistake, contact support."),

    // ---- any document
    PHOTO_UNREADABLE(Scope.DOCUMENT, false,
            "The photo is blurry or cut off. Take it again flat, in good light, with all four corners showing."),
    NUMBER_MISMATCH(Scope.DOCUMENT, false,
            "The number you typed is not the number on the {document}. Send it again and type the number exactly as printed."),
    DATE_MISMATCH(Scope.DOCUMENT, false,
            "The date you typed is not the date on the {document}. Send it again with the date printed on it."),
    DOCUMENT_EXPIRED(Scope.DOCUMENT, false,
            "This {document} has expired. Upload your renewed {document}."),
    DOCUMENT_ALTERED(Scope.DOCUMENT, true,
            "We could not accept this {document}. If you think this is a mistake, contact support."),
    OTHER_VEHICLE(Scope.VEHICLE_PAPER, false,
            "This {document} is for a different vehicle. Send the one for your vehicle."),

    // ---- licence
    NOT_IN_GOVERNMENT_RECORD(Scope.LICENCE_OR_RC, false,
            "We could not find this {document} in the government record. Check the number and send it again."),
    NOT_HERS(Scope.LICENCE_OR_RC, true,
            "The government record shows this {document} belongs to someone else. Send your own, or contact support."),
    LEARNER_LICENCE(Scope.LICENCE, false,
            "This is a learner's licence. Send your permanent driving licence."),
    LICENCE_NOT_TWO_WHEELER(Scope.LICENCE, false,
            "This licence is not for two-wheelers. It needs MCWG or MCWOG under class of vehicle."),
    LICENCE_NAME_MISMATCH(Scope.LICENCE, false,
            "The name on the licence is not the name on your ID. Send your own licence, or contact support if your name has changed."),

    // ---- RC
    PLATE_MISMATCH(Scope.RC, false,
            "The vehicle number on the RC is not the number on your profile. Correct it in Profile, or send the RC for your vehicle."),
    RC_NOT_TWO_WHEELER(Scope.RC, false,
            "This RC is not for a two-wheeler. SheOut takes scooters and motorcycles only."),
    OWNER_MISMATCH(Scope.RC, false,
            "The owner's name on the RC is not what you told us. Send it again and choose whose vehicle it is."),

    // ---- insurance
    NOT_COMMERCIAL(Scope.INSURANCE, false,
            "This is a private policy. Riders need a commercial policy. Ask your insurer for one.");

    /** What a reason can be given for. */
    public enum Scope {
        IDENTITY, DOCUMENT, VEHICLE_PAPER, LICENCE_OR_RC, LICENCE, RC, INSURANCE;

        boolean covers(PartnerDocumentType type) {
            return switch (this) {
                case IDENTITY -> false;
                case DOCUMENT -> true;
                case VEHICLE_PAPER -> EnumSet.of(PartnerDocumentType.VEHICLE_RC, PartnerDocumentType.VEHICLE_INSURANCE,
                        PartnerDocumentType.PUC, PartnerDocumentType.FITNESS_CERTIFICATE).contains(type);
                case LICENCE_OR_RC -> type == PartnerDocumentType.DRIVING_LICENCE || type == PartnerDocumentType.VEHICLE_RC;
                case LICENCE -> type == PartnerDocumentType.DRIVING_LICENCE;
                case RC -> type == PartnerDocumentType.VEHICLE_RC;
                case INSURANCE -> type == PartnerDocumentType.VEHICLE_INSURANCE;
            };
        }
    }

    private final Scope scope;
    private final boolean remembersNumber;
    private final String message;

    RejectionReason(Scope scope, boolean remembersNumber, String message) {
        this.scope = scope;
        this.remembersNumber = remembersNumber;
        this.message = message;
    }

    public Scope scope() {
        return scope;
    }

    /** Only fraud and a confirmed mismatch - see the class comment. */
    public boolean remembersNumber() {
        return remembersNumber;
    }

    /** May be given for her ID and selfie. */
    public boolean forIdentity() {
        return scope == Scope.IDENTITY;
    }

    /** May be given for this document. */
    public boolean appliesTo(PartnerDocumentType type) {
        return scope.covers(type);
    }

    /** What she is shown, in English, for this document (null for her ID). */
    public String message(PartnerDocumentType type) {
        String name = type == null ? "document" : switch (type) {
            case DRIVING_LICENCE -> "licence";
            case VEHICLE_RC -> "RC";
            case VEHICLE_INSURANCE -> "policy";
            case PUC -> "PUC certificate";
            case FITNESS_CERTIFICATE -> "fitness certificate";
            case POLICE_CERTIFICATE -> "police certificate";
            case BACKGROUND_CHECK_REPORT -> "report";
        };
        return message.replace("{document}", name);
    }

    /** Every reason that may be given for her ID, or for this document - what the console lists. */
    public static Set<RejectionReason> forTarget(PartnerDocumentType type) {
        Set<RejectionReason> out = EnumSet.noneOf(RejectionReason.class);
        for (RejectionReason r : values()) {
            if (type == null ? r.forIdentity() : r.appliesTo(type)) {
                out.add(r);
            }
        }
        return out;
    }
}
