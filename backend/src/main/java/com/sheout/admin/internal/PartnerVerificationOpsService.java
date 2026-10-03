package com.sheout.admin.internal;

import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationAuditEntry;
import com.sheout.driververification.VerificationSummary;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import com.sheout.users.OnlineStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The console's view of partners' paperwork: her Documents tab, and the four
 * queues (to review, expiring, expired, police check due). Joins
 * driver-verification's facts with the names, phones and vehicles users and
 * auth own; stores nothing.
 */
@Service
public class PartnerVerificationOpsService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    /** Which queue - see documentQueue. */
    public enum Queue {
        TO_REVIEW,
        EXPIRING,
        EXPIRED,
        POLICE_DUE
    }

    /** "Expiring" and "police check due" look this far ahead - the first reminder's window. */
    static final int LOOK_AHEAD_DAYS = 30;

    private final VerificationApi verification;
    private final DriverProfileApi driverProfiles;
    private final AuthApi auth;

    PartnerVerificationOpsService(VerificationApi verification, DriverProfileApi driverProfiles, AuthApi auth) {
        this.verification = verification;
        this.driverProfiles = driverProfiles;
        this.auth = auth;
    }

    public Optional<PartnerVerificationView> partner(UUID accountId) {
        Optional<VerificationSummary> summary = verification.findByAccountId(accountId);
        if (summary.isEmpty()) {
            return Optional.empty();
        }
        Optional<DriverProfileSummary> profile = driverProfiles.findByAccountId(accountId);
        String vehicle = profile.map(p -> p.vehicleType() == null ? null : p.vehicleType().name()).orElse(null);
        Map<UUID, String> actors = new HashMap<>();
        List<PartnerVerificationView.AuditRow> audit = verification.auditTrail(accountId).stream()
                .map(e -> auditRow(e, actors)).toList();
        return Optional.of(new PartnerVerificationView(
                accountId,
                profile.map(DriverProfileSummary::name).orElse(null),
                phoneOf(accountId),
                vehicle,
                profile.map(DriverProfileSummary::vehicleRegistrationNumber).orElse(null),
                summary.get().genderVerificationStatus(),
                summary.get().policeVerificationStatus(),
                verification.policeReverifyDueOn(accountId).orElse(null),
                verification.partnerReadiness(accountId, vehicle),
                verification.findPartnerDocuments(accountId),
                verification.policeHistory(accountId),
                verification.consentStatus(accountId),
                audit,
                verification.policeReverifyMonths()));
    }

    public List<DocumentQueueRow> documentQueue(Queue queue) {
        LocalDate today = LocalDate.now(INDIA);
        Map<UUID, Optional<DriverProfileSummary>> profiles = new HashMap<>();
        return switch (queue) {
            case TO_REVIEW -> verification.findDocumentsAwaitingReview().stream()
                    .map(d -> row(d, today, profiles)).toList();
            case EXPIRING -> verification.findDocumentsExpiringWithin(LOOK_AHEAD_DAYS).stream()
                    .map(d -> row(d, today, profiles)).toList();
            case EXPIRED -> verification.findExpiredDocuments().stream()
                    .map(d -> row(d, today, profiles)).toList();
            case POLICE_DUE -> verification.findPoliceReverificationDueWithin(LOOK_AHEAD_DAYS).stream()
                    .map(s -> policeRow(s, today, profiles))
                    .sorted(Comparator.comparing(DocumentQueueRow::validUntil, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        };
    }

    private DocumentQueueRow row(PartnerDocumentSummary d, LocalDate today, Map<UUID, Optional<DriverProfileSummary>> profiles) {
        Optional<DriverProfileSummary> p = profiles.computeIfAbsent(d.accountId(), driverProfiles::findByAccountId);
        return new DocumentQueueRow(d.accountId(), p.map(DriverProfileSummary::name).orElse(null),
                p.map(DriverProfileSummary::profilePhotoUrl).orElse(null), phoneOf(d.accountId()),
                p.map(x -> x.vehicleType() == null ? null : x.vehicleType().name()).orElse(null),
                p.map(DriverProfileSummary::vehicleRegistrationNumber).orElse(null),
                d.id(), d.type(), d.status(), d.documentNumber(), d.validUntil(),
                d.validUntil() == null ? null : ChronoUnit.DAYS.between(today, d.validUntil()),
                d.submittedAt(), p.map(x -> x.onlineStatus() == OnlineStatus.ONLINE).orElse(false),
                isBlocked(d.accountId()));
    }

    private DocumentQueueRow policeRow(VerificationSummary s, LocalDate today, Map<UUID, Optional<DriverProfileSummary>> profiles) {
        Optional<DriverProfileSummary> p = profiles.computeIfAbsent(s.accountId(), driverProfiles::findByAccountId);
        LocalDate due = verification.policeReverifyDueOn(s.accountId()).orElse(null);
        return new DocumentQueueRow(s.accountId(), p.map(DriverProfileSummary::name).orElse(null),
                p.map(DriverProfileSummary::profilePhotoUrl).orElse(null), phoneOf(s.accountId()),
                p.map(x -> x.vehicleType() == null ? null : x.vehicleType().name()).orElse(null),
                p.map(DriverProfileSummary::vehicleRegistrationNumber).orElse(null),
                null, PartnerDocumentType.POLICE_CERTIFICATE, PartnerDocumentStatus.VERIFIED, null, due,
                due == null ? null : ChronoUnit.DAYS.between(today, due), null,
                p.map(x -> x.onlineStatus() == OnlineStatus.ONLINE).orElse(false), isBlocked(s.accountId()));
    }

    private PartnerVerificationView.AuditRow auditRow(VerificationAuditEntry e, Map<UUID, String> actors) {
        String actor = e.actorId() == null ? null : actors.computeIfAbsent(e.actorId(), this::phoneOf);
        return new PartnerVerificationView.AuditRow(actor, e.actorRole(), e.action(),
                e.documentType() == null ? null : e.documentType().name(), e.documentId(), e.detail(), e.at());
    }

    private String phoneOf(UUID accountId) {
        return auth.findAccount(accountId).map(AccountSummary::phoneNumber).orElse(null);
    }

    private boolean isBlocked(UUID accountId) {
        return auth.findAccount(accountId).map(AccountSummary::blocked).orElse(false);
    }
}
