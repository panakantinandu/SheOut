package com.sheout.driververification.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.driververification.CourtScreeningStatus;
import com.sheout.driververification.internal.CourtScreeningEntity;
import com.sheout.driververification.internal.CourtScreeningService;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static com.sheout.driververification.internal.web.AdminCourtScreeningController.unwrap;

/**
 * The partner's side of a court-record screening: agree to it (or withdraw),
 * and answer when told a record may match her name. She never sees case
 * details or reviewers' notes - only where it stands, in neutral words the
 * app writes from the status.
 * <p>
 * 404 when the feature is off or she has no screening: the same answer
 * either way.
 */
@RestController
public class CourtScreeningController {

    private final CourtScreeningService service;

    public CourtScreeningController(CourtScreeningService service) {
        this.service = service;
    }

    /** What she sees. No notes, no case details, no reviewer. */
    public record PartnerView(CourtScreeningStatus status, String consentVersion, boolean consentNeeded,
                              Instant responseDueAt, boolean responded) {
        static PartnerView of(CourtScreeningEntity s, String currentVersion) {
            return new PartnerView(s.getStatus(), currentVersion, s.getStatus() == CourtScreeningStatus.CONSENT_REQUESTED,
                    s.getResponseDueAt(), s.getPartnerRespondedAt() != null);
        }
    }

    @GetMapping("/api/v1/driver-verification/court-screening")
    public ResponseEntity<PartnerView> mine() {
        CurrentAccount me = partner();
        return service.forPartner(me.accountId())
                .map(s -> ResponseEntity.ok(PartnerView.of(s, service.consentVersion())))
                .orElseThrow(() -> ApiException.notFound("Not found"));
    }

    public record Consent(@NotNull @Size(max = 40) String version) {
    }

    @PostMapping("/api/v1/driver-verification/court-screening/consent")
    public ResponseEntity<PartnerView> consent(@Valid @RequestBody Consent body) {
        CurrentAccount me = partner();
        return ResponseEntity.ok(PartnerView.of(unwrap(service.consent(me.accountId(), body.version())), service.consentVersion()));
    }

    @PostMapping("/api/v1/driver-verification/court-screening/withdraw")
    public ResponseEntity<PartnerView> withdraw() {
        CurrentAccount me = partner();
        return ResponseEntity.ok(PartnerView.of(unwrap(service.withdraw(me.accountId())), service.consentVersion()));
    }

    public record Response(@NotNull @Size(min = 1, max = 1000) String text) {
    }

    @PostMapping("/api/v1/driver-verification/court-screening/response")
    public ResponseEntity<PartnerView> respond(@Valid @RequestBody Response body) {
        CurrentAccount me = partner();
        return ResponseEntity.ok(PartnerView.of(unwrap(service.respond(me.accountId(), body.text())), service.consentVersion()));
    }

    private static CurrentAccount partner() {
        CurrentAccount caller = VerificationController.requireAuthenticated();
        if (caller.role() != AccountRole.DRIVER) {
            throw ApiException.notFound("Not found");
        }
        return caller;
    }
}
