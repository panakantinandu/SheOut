package com.sheout.insurance.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.insurance.EnrolmentStatus;
import com.sheout.insurance.PolicyKind;
import com.sheout.insurance.PremiumUnit;
import com.sheout.insurance.internal.InsurancePolicyEntity;
import com.sheout.insurance.internal.InsuranceService;
import com.sheout.insurance.internal.PartnerEnrolmentEntity;
import com.sheout.insurance.internal.reporting.InsurerReporter;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.users.DriverProfileApi;
import com.sheout.users.DriverProfileSummary;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * The console's insurance screens: policies (entered from the insurer's
 * schedule - nothing is hardcoded), whether trips are covered right now,
 * the daily bordereau, the monthly premium SheOut owes, and partners'
 * group-cover enrolments with the monthly joiners-and-leavers file.
 */
@RestController
public class AdminInsuranceController {

    private final InsuranceService insurance;
    private final DriverProfileApi driverProfiles;
    private final AuthApi auth;

    public AdminInsuranceController(InsuranceService insurance, DriverProfileApi driverProfiles, AuthApi auth) {
        this.insurance = insurance;
        this.driverProfiles = driverProfiles;
        this.auth = auth;
    }

    // ---------------------------------------------------------------- policies

    @GetMapping("/api/v1/admin/insurance/policies")
    public ResponseEntity<List<PolicyView>> policies() {
        requireAdmin();
        return ResponseEntity.ok(insurance.allPolicies().stream().map(this::view).toList());
    }

    @PostMapping("/api/v1/admin/insurance/policies")
    public ResponseEntity<PolicyView> createPolicy(@RequestBody PolicyRequest request) {
        CurrentAccount admin = requireAdmin();
        return ResponseEntity.ok(view(orThrow(insurance.createPolicy(request.toInput(), admin.accountId()))));
    }

    @PutMapping("/api/v1/admin/insurance/policies/{id}")
    public ResponseEntity<PolicyView> updatePolicy(@PathVariable UUID id, @RequestBody PolicyRequest request) {
        requireAdmin();
        return ResponseEntity.ok(view(orThrow(insurance.updatePolicy(id, request.toInput()))));
    }

    @PostMapping("/api/v1/admin/insurance/policies/{id}/activate")
    public ResponseEntity<PolicyView> activate(@PathVariable UUID id) {
        requireAdmin();
        return ResponseEntity.ok(view(orThrow(insurance.setActive(id, true))));
    }

    @PostMapping("/api/v1/admin/insurance/policies/{id}/deactivate")
    public ResponseEntity<PolicyView> deactivate(@PathVariable UUID id) {
        requireAdmin();
        return ResponseEntity.ok(view(orThrow(insurance.setActive(id, false))));
    }

    // ------------------------------------------------------------ status, files

    /** Whether trips are covered right now, and how reporting stands - the console's banner and tiles. */
    @GetMapping("/api/v1/admin/insurance/status")
    public ResponseEntity<InsuranceService.Status> status() {
        requireAdmin();
        return ResponseEntity.ok(insurance.status());
    }

    /** One day's covered trips, as the configured reporter produces them (a CSV by default). */
    @GetMapping("/api/v1/admin/insurance/bordereau")
    public ResponseEntity<byte[]> bordereau(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireAdmin();
        Result<InsurerReporter.Report, String> result = insurance.bordereau(date);
        if (result.isFailure()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "REPORT_FAILED",
                    "The day's trips could not be reported: " + result.error()
                            + " They are marked as not reported, and the apps no longer call them insured.");
        }
        InsurerReporter.Report report = result.value();
        return file(report.file(), report.contentType(), report.fileName());
    }

    /** The premium SheOut owes for trips started in a month, per policy. A platform cost, never a fare line. */
    @GetMapping("/api/v1/admin/insurance/premium-report")
    public ResponseEntity<List<InsuranceService.PremiumRow>> premiumReport(@RequestParam String month) {
        requireAdmin();
        return ResponseEntity.ok(insurance.premiumReport(parseMonth(month)));
    }

    // -------------------------------------------------------------- enrolments

    @GetMapping("/api/v1/admin/insurance/enrolments")
    public ResponseEntity<List<EnrolmentView>> enrolments() {
        requireAdmin();
        return ResponseEntity.ok(insurance.allEnrolments().stream().map(this::view).toList());
    }

    /** The insurer confirmed her: she is ENROLLED with the member id it gave, and her app shows the cover. */
    @PostMapping("/api/v1/admin/insurance/enrolments/{id}/enrolled")
    public ResponseEntity<EnrolmentView> markEnrolled(@PathVariable UUID id, @RequestBody EnrolRequest request) {
        requireAdmin();
        return ResponseEntity.ok(view(orThrowEnrolment(insurance.markEnrolled(id, request == null ? null : request.memberId()))));
    }

    @PostMapping("/api/v1/admin/insurance/enrolments/{id}/exited")
    public ResponseEntity<EnrolmentView> markExited(@PathVariable UUID id, @RequestBody(required = false) ExitRequest request) {
        requireAdmin();
        return ResponseEntity.ok(view(orThrowEnrolment(insurance.markExited(id, request == null ? null : request.reason()))));
    }

    /**
     * The month's joiners and leavers, for the insurer's member list. Her
     * name, date of birth and phone number are what an insurer needs to
     * enrol her - and what the partner consent says it receives.
     */
    @GetMapping("/api/v1/admin/insurance/enrolments/movements")
    public ResponseEntity<byte[]> movements(@RequestParam String month) {
        requireAdmin();
        YearMonth ym = parseMonth(month);
        InsuranceService.Movements m = insurance.movements(ym);
        StringBuilder csv = new StringBuilder("movement,date,insurer,policy_number,member_id,partner_name,date_of_birth,phone,account_id\r\n");
        m.joiners().forEach(e -> csv.append(movementLine("JOINER", e.getEnrolledOn(), e)));
        m.leavers().forEach(e -> csv.append(movementLine("LEAVER", e.getExitedOn(), e)));
        return file(csv.toString().getBytes(StandardCharsets.UTF_8), "text/csv; charset=utf-8",
                "sheout-partner-cover-movements-" + ym + ".csv");
    }

    private String movementLine(String movement, LocalDate on, PartnerEnrolmentEntity e) {
        InsurancePolicyEntity p = insurance.policy(e.getPolicyId()).orElse(null);
        DriverProfileSummary profile = driverProfiles.findByAccountId(e.getAccountId()).orElse(null);
        String phone = auth.findAccount(e.getAccountId()).map(AccountSummary::phoneNumber).orElse(null);
        return String.join(",", movement, String.valueOf(on), cell(p == null ? null : p.getInsurerName()),
                cell(p == null ? null : p.getMasterPolicyNumber()), cell(e.getMemberId()),
                cell(profile == null ? null : profile.name()),
                profile == null || profile.dateOfBirth() == null ? "" : profile.dateOfBirth().toString(),
                cell(phone), e.getAccountId().toString()) + "\r\n";
    }

    // ------------------------------------------------------------------ shapes

    public record PolicyRequest(PolicyKind kind, @Size(max = 150) String insurerName,
                                @Size(max = 80) String masterPolicyNumber, BigDecimal sumInsured,
                                BigDecimal premiumPerUnit, PremiumUnit premiumUnit, LocalDate effectiveFrom,
                                LocalDate effectiveTo, @Size(max = 30) String claimsPhone,
                                @Size(max = 500) String claimsUrl, @Size(max = 500) String policySummaryUrl,
                                @Size(max = 2000) String coverageSummary, @Size(max = 2000) String claimSteps) {
        InsuranceService.PolicyInput toInput() {
            return new InsuranceService.PolicyInput(kind, insurerName, masterPolicyNumber, sumInsured, premiumPerUnit,
                    premiumUnit, effectiveFrom, effectiveTo, claimsPhone, claimsUrl, policySummaryUrl, coverageSummary,
                    claimSteps);
        }
    }

    public record PolicyView(UUID id, PolicyKind kind, String insurerName, String masterPolicyNumber,
                             BigDecimal sumInsured, BigDecimal premiumPerUnit, PremiumUnit premiumUnit,
                             LocalDate effectiveFrom, LocalDate effectiveTo, String claimsPhone, String claimsUrl,
                             String policySummaryUrl, String coverageSummary, String claimSteps, boolean active,
                             boolean inForceToday, Instant updatedAt) {
    }

    public record EnrolmentView(UUID id, UUID accountId, String partnerName, String phoneNumber, UUID policyId,
                                PolicyKind kind, String insurerName, String policyNumber, String memberId,
                                EnrolmentStatus status, LocalDate enrolledOn, LocalDate exitedOn, String exitReason,
                                Instant createdAt) {
    }

    public record EnrolRequest(@Size(max = 80) String memberId) {
    }

    public record ExitRequest(@Size(max = 200) String reason) {
    }

    private PolicyView view(InsurancePolicyEntity p) {
        return new PolicyView(p.getId(), p.getKind(), p.getInsurerName(), p.getMasterPolicyNumber(), p.getSumInsured(),
                p.getPremiumPerUnit(), p.getPremiumUnit(), p.getEffectiveFrom(), p.getEffectiveTo(), p.getClaimsPhone(),
                p.getClaimsUrl(), p.getPolicySummaryUrl(), p.getCoverageSummary(), p.getClaimSteps(), p.isActive(),
                p.inForceOn(LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))), p.getUpdatedAt());
    }

    private EnrolmentView view(PartnerEnrolmentEntity e) {
        InsurancePolicyEntity p = insurance.policy(e.getPolicyId()).orElse(null);
        return new EnrolmentView(e.getId(), e.getAccountId(),
                driverProfiles.findByAccountId(e.getAccountId()).map(DriverProfileSummary::name).orElse(null),
                auth.findAccount(e.getAccountId()).map(AccountSummary::phoneNumber).orElse(null),
                e.getPolicyId(), p == null ? null : p.getKind(), p == null ? null : p.getInsurerName(),
                p == null ? null : p.getMasterPolicyNumber(), e.getMemberId(), e.getStatus(), e.getEnrolledOn(),
                e.getExitedOn(), e.getExitReason(), e.getCreatedAt());
    }

    private static InsurancePolicyEntity orThrow(Result<InsurancePolicyEntity, InsuranceService.PolicyError> result) {
        if (result.isSuccess()) {
            return result.value();
        }
        throw switch (result.error()) {
            case POLICY_NOT_FOUND -> ApiException.notFound("No such policy");
            case INVALID_POLICY -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POLICY",
                    "Fill in the kind, insurer, policy number, a sum insured above zero, the premium, how it is charged,"
                            + " and the date it starts. An end date cannot be before the start.");
            case PREMIUM_UNIT_MISMATCH -> new ApiException(HttpStatus.BAD_REQUEST, "PREMIUM_UNIT_MISMATCH",
                    "Trip and goods cover are charged per trip; a partner's health, life or accident cover per member per year.");
            case KIND_ALREADY_ACTIVE -> new ApiException(HttpStatus.CONFLICT, "KIND_ALREADY_ACTIVE",
                    "Another policy of this kind is active. Deactivate it first, so every trip has exactly one policy.");
            case ACTIVE_POLICY_LOCKED -> new ApiException(HttpStatus.CONFLICT, "ACTIVE_POLICY_LOCKED",
                    "An active policy cannot be edited: trips already covered point at it. Deactivate it and add the new terms as a new policy.");
        };
    }

    private static PartnerEnrolmentEntity orThrowEnrolment(Result<PartnerEnrolmentEntity, InsuranceService.EnrolmentError> result) {
        if (result.isSuccess()) {
            return result.value();
        }
        throw switch (result.error()) {
            case NOT_FOUND -> ApiException.notFound("No such enrolment");
            case MEMBER_ID_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "MEMBER_ID_REQUIRED",
                    "Enter the member id the insurer gave her. She is shown as covered only once it is recorded.");
            case ALREADY_EXITED -> new ApiException(HttpStatus.CONFLICT, "ALREADY_EXITED", "She has already left this cover.");
        };
    }

    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "Month must look like 2026-10");
        }
    }

    private static ResponseEntity<byte[]> file(byte[] body, String contentType, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
    }

    /** CSV-safe, and a leading formula character neutralised - see CsvBordereauReporter.cell. */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }
}
