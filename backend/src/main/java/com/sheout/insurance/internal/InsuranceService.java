package com.sheout.insurance.internal;

import com.sheout.auth.AccountBlocked;
import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingStarted;
import com.sheout.booking.BookingType;
import com.sheout.driververification.AccountVerified;
import com.sheout.insurance.CoverSummary;
import com.sheout.insurance.EnrolmentStatus;
import com.sheout.insurance.InsuranceApi;
import com.sheout.insurance.PolicyKind;
import com.sheout.insurance.PremiumUnit;
import com.sheout.insurance.TripCover;
import com.sheout.insurance.internal.reporting.InsurerReporter;
import com.sheout.privacy.AccountDeletionRequested;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Master policies, the cover each trip had, and partners' group covers.
 * <p>
 * Cover is built in. When a trip starts and a policy of the right kind is in
 * force, a coverage row is opened with the premium SheOut pays for it; when
 * the trip ends, it is closed. The premium is a platform cost and nothing
 * else: the rider's fare, the partner's share and the commission check are
 * computed in other modules from numbers this never touches.
 * <p>
 * With no policy in force, no row is opened, and the apps - which ask
 * coverForTrip - say nothing about insurance. That is the whole of the
 * "never claim what is not true" rule here: the claim is made only from a
 * row that exists.
 * <p>
 * Depends on nothing in booking but its events, on purpose: booking asks
 * this module whether rides are covered, so this one cannot ask booking
 * anything without the two waiting on each other at startup.
 */
@Service
public class InsuranceService implements InsuranceApi {

    private static final Logger log = LoggerFactory.getLogger(InsuranceService.class);
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final InsurancePolicyRepository policies;
    private final TripCoverageRepository coverages;
    private final PartnerEnrolmentRepository enrolments;
    private final InsurerReporter reporter;
    private final boolean badgeRequiresReported;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public InsuranceService(InsurancePolicyRepository policies, TripCoverageRepository coverages,
                            PartnerEnrolmentRepository enrolments, InsurerReporter reporter,
                            @Value("${sheout.insurance.badge-requires-reported:false}") boolean badgeRequiresReported) {
        this(policies, coverages, enrolments, reporter, badgeRequiresReported, Clock.systemUTC());
    }

    InsuranceService(InsurancePolicyRepository policies, TripCoverageRepository coverages,
                     PartnerEnrolmentRepository enrolments, InsurerReporter reporter, boolean badgeRequiresReported,
                     Clock clock) {
        this.policies = policies;
        this.coverages = coverages;
        this.enrolments = enrolments;
        this.reporter = reporter;
        this.badgeRequiresReported = badgeRequiresReported;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(INDIA));
    }

    // ------------------------------------------------------------- InsuranceApi

    @Override
    @Transactional(readOnly = true)
    public boolean passengerCoverActive() {
        return inForce(PolicyKind.PASSENGER_TRIP).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CoverSummary> currentPassengerCover() {
        return inForce(PolicyKind.PASSENGER_TRIP).map(p -> summary(p, null));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TripCover> coverForTrip(UUID bookingId) {
        return coverages.findByBookingId(bookingId)
                .filter(c -> c.getReportedStatus() != TripCoverageEntity.ReportedStatus.FAILED)
                .filter(c -> !badgeRequiresReported || c.getReportedStatus() == TripCoverageEntity.ReportedStatus.REPORTED)
                .flatMap(c -> policies.findById(c.getPolicyId()).map(p -> new TripCover(c.getBookingId(), p.getKind(),
                        p.getInsurerName(), p.getMasterPolicyNumber(), p.getSumInsured(), p.getCoverageSummary(),
                        p.getClaimSteps(), p.getClaimsPhone(), p.getClaimsUrl(), p.getPolicySummaryUrl(),
                        c.getCoverageStartedAt(), c.getCoverageEndedAt())));
    }

    /** Participants only - for the controller, which must not show one rider another's trip. */
    @Transactional(readOnly = true)
    public boolean isParticipant(UUID bookingId, UUID accountId) {
        return coverages.findByBookingId(bookingId)
                .map(c -> accountId.equals(c.getRiderAccountId()) || accountId.equals(c.getPartnerAccountId()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoverSummary> enrolledCoversFor(UUID partnerAccountId) {
        List<CoverSummary> out = new ArrayList<>();
        for (PartnerEnrolmentEntity e : enrolments.findByAccountId(partnerAccountId)) {
            if (e.getStatus() != EnrolmentStatus.ENROLLED) {
                continue;
            }
            policies.findById(e.getPolicyId()).ifPresent(p -> out.add(summary(p, e.getMemberId())));
        }
        return out;
    }

    // ---------------------------------------------------------- trip coverage

    /**
     * The trip has started: open its cover if a policy of the right kind is
     * in force. Joined to booking's transaction, so a trip start that rolls
     * back leaves no cover behind, and one that commits cannot lose it.
     */
    @EventListener
    @Transactional
    public void onBookingStarted(BookingStarted event) {
        if (event.category() == null || coverages.findByBookingId(event.bookingId()).isPresent()) {
            return;
        }
        PolicyKind kind = kindFor(event.category());
        Optional<InsurancePolicyEntity> policy = inForce(kind);
        if (policy.isEmpty()) {
            // Nothing to cover it with. Said in the log, so "why does this
            // trip have no cover" has an answer beyond the console's banner.
            log.warn("Trip {} started with no {} policy in force: not covered", event.bookingId(), kind);
            return;
        }
        InsurancePolicyEntity p = policy.get();
        coverages.save(new TripCoverageEntity(event.bookingId(), p.getId(), event.customerId(), event.driverId(),
                event.category().name(),
                event.pickup() == null ? null : AreaName.coarse(event.pickup().label()),
                event.drop() == null ? null : AreaName.coarse(event.drop().label()),
                clock.instant(), premiumPerTrip(p)));
    }

    @EventListener
    @Transactional
    public void onBookingCompleted(BookingCompleted event) {
        endCover(event.bookingId());
    }

    /** Booking cancels only before a trip starts today; if that ever changes, the cover still ends. */
    @EventListener
    @Transactional
    public void onBookingCancelled(BookingCancelled event) {
        endCover(event.bookingId());
    }

    private void endCover(UUID bookingId) {
        coverages.findByBookingId(bookingId).ifPresent(c -> {
            c.end(clock.instant());
            coverages.save(c);
        });
    }

    /** Rides are passenger cover; deliveries are goods in transit. */
    static PolicyKind kindFor(BookingCategory category) {
        return category.expectedType() == BookingType.RIDE ? PolicyKind.PASSENGER_TRIP : PolicyKind.GOODS_IN_TRANSIT;
    }

    /** What SheOut pays for one trip: the per-trip premium, or nothing for a policy priced per member. */
    static BigDecimal premiumPerTrip(InsurancePolicyEntity p) {
        return p.getPremiumUnit() == PremiumUnit.PER_TRIP ? p.getPremiumPerUnit() : BigDecimal.ZERO.setScale(2);
    }

    // ------------------------------------------------------- partner enrolment

    /**
     * A partner verified: she is put forward for each group cover in force,
     * as PENDING_ENROLMENT. Not shown to her as cover until the insurer has
     * confirmed her and an operator marks her ENROLLED.
     */
    @EventListener
    @Transactional
    public void onAccountVerified(AccountVerified event) {
        if (event.role() != AccountRole.DRIVER) {
            return;
        }
        for (InsurancePolicyEntity p : policies.findByActiveTrue()) {
            if (p.getKind().partnerCover() && p.inForceOn(today())
                    && enrolments.findFirstByAccountIdAndPolicyIdAndStatusNot(event.accountId(), p.getId(),
                    EnrolmentStatus.EXITED).isEmpty()) {
                enrolments.save(new PartnerEnrolmentEntity(event.accountId(), p.getId()));
            }
        }
    }

    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        exitAll(event.accountId(), "Account deleted");
    }

    @EventListener
    @Transactional
    public void onAccountBlocked(AccountBlocked event) {
        if (event.role() == AccountRole.DRIVER) {
            exitAll(event.accountId(), "Account blocked");
        }
    }

    private void exitAll(UUID accountId, String reason) {
        for (PartnerEnrolmentEntity e : enrolments.findByAccountId(accountId)) {
            if (e.getStatus() != EnrolmentStatus.EXITED) {
                e.exit(today(), reason);
                enrolments.save(e);
            }
        }
    }

    // -------------------------------------------------- operators: policies

    public record PolicyInput(PolicyKind kind, String insurerName, String masterPolicyNumber, BigDecimal sumInsured,
                              BigDecimal premiumPerUnit, PremiumUnit premiumUnit, LocalDate effectiveFrom,
                              LocalDate effectiveTo, String claimsPhone, String claimsUrl, String policySummaryUrl,
                              String coverageSummary, String claimSteps) {
    }

    public enum PolicyError {
        POLICY_NOT_FOUND,
        /** A required field is missing or out of range - the message says which. */
        INVALID_POLICY,
        /** A per-trip kind priced per member, or the other way round. */
        PREMIUM_UNIT_MISMATCH,
        /** Another policy of this kind is already active; deactivate it first. */
        KIND_ALREADY_ACTIVE,
        /** An active policy is not edited: past trips point at it. Deactivate it and add the new terms as a new policy. */
        ACTIVE_POLICY_LOCKED
    }

    @Transactional(readOnly = true)
    public List<InsurancePolicyEntity> allPolicies() {
        return policies.findAllByOrderByActiveDescEffectiveFromDesc();
    }

    @Transactional
    public Result<InsurancePolicyEntity, PolicyError> createPolicy(PolicyInput in, UUID operatorId) {
        PolicyError problem = check(in);
        if (problem != null) {
            return Result.failure(problem);
        }
        InsurancePolicyEntity p = new InsurancePolicyEntity(operatorId);
        apply(p, in);
        return Result.success(policies.save(p));
    }

    @Transactional
    public Result<InsurancePolicyEntity, PolicyError> updatePolicy(UUID id, PolicyInput in) {
        Optional<InsurancePolicyEntity> found = policies.findById(id);
        if (found.isEmpty()) {
            return Result.failure(PolicyError.POLICY_NOT_FOUND);
        }
        if (found.get().isActive()) {
            return Result.failure(PolicyError.ACTIVE_POLICY_LOCKED);
        }
        PolicyError problem = check(in);
        if (problem != null) {
            return Result.failure(problem);
        }
        apply(found.get(), in);
        return Result.success(policies.save(found.get()));
    }

    /**
     * Switching a policy on or off. One active policy per kind: two would
     * leave "which one covered this trip" to whichever the database returned
     * first.
     */
    @Transactional
    public Result<InsurancePolicyEntity, PolicyError> setActive(UUID id, boolean active) {
        Optional<InsurancePolicyEntity> found = policies.findById(id);
        if (found.isEmpty()) {
            return Result.failure(PolicyError.POLICY_NOT_FOUND);
        }
        InsurancePolicyEntity p = found.get();
        if (active && policies.findByKindAndActiveTrue(p.getKind()).stream().anyMatch(o -> !id.equals(o.getId()))) {
            return Result.failure(PolicyError.KIND_ALREADY_ACTIVE);
        }
        p.setActive(active);
        return Result.success(policies.save(p));
    }

    private static PolicyError check(PolicyInput in) {
        if (in.kind() == null || blank(in.insurerName()) || blank(in.masterPolicyNumber()) || in.sumInsured() == null
                || in.sumInsured().signum() <= 0 || in.premiumPerUnit() == null || in.premiumPerUnit().signum() < 0
                || in.premiumUnit() == null || in.effectiveFrom() == null
                || (in.effectiveTo() != null && in.effectiveTo().isBefore(in.effectiveFrom()))) {
            return PolicyError.INVALID_POLICY;
        }
        boolean perMember = in.premiumUnit() == PremiumUnit.PER_MEMBER_PER_YEAR;
        if (in.kind().partnerCover() != perMember) {
            return PolicyError.PREMIUM_UNIT_MISMATCH;
        }
        return null;
    }

    private static void apply(InsurancePolicyEntity p, PolicyInput in) {
        p.update(in.kind(), in.insurerName().trim(), in.masterPolicyNumber().trim(), in.sumInsured(),
                in.premiumPerUnit(), in.premiumUnit(), in.effectiveFrom(), in.effectiveTo(), trim(in.claimsPhone()),
                trim(in.claimsUrl()), trim(in.policySummaryUrl()), trim(in.coverageSummary()), trim(in.claimSteps()));
    }

    // --------------------------------------------------- operators: status

    public record Status(boolean passengerCoverActive, boolean goodsCoverActive, long tripsCoveredToday,
                         long notYetReported, long notReportedAfterADay, long failedReports,
                         BigDecimal premiumThisMonth, String reporter) {
    }

    @Transactional(readOnly = true)
    public Status status() {
        LocalDate today = today();
        Instant startOfToday = today.atStartOfDay(INDIA).toInstant();
        YearMonth month = YearMonth.from(today);
        BigDecimal premium = premiumReport(month).stream().map(PremiumRow::premium).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Status(passengerCoverActive(), inForce(PolicyKind.GOODS_IN_TRANSIT).isPresent(),
                coverages.countByCoverageStartedAtGreaterThanEqual(startOfToday),
                coverages.countByReportedStatus(TripCoverageEntity.ReportedStatus.PENDING),
                coverages.countByReportedStatusAndCoverageStartedAtLessThan(TripCoverageEntity.ReportedStatus.PENDING,
                        startOfToday.minusSeconds(86400)),
                coverages.countByReportedStatus(TripCoverageEntity.ReportedStatus.FAILED), premium, reporter.name());
    }

    // ----------------------------------------------------- operators: reports

    /**
     * One day's covered trips, declared through the configured reporter.
     * With the CSV reporter the file is handed back to the operator to send,
     * and the trips are marked REPORTED - meaning "in a bordereau an operator
     * downloaded to send", which is as far as SheOut's own records can see.
     * A reporter that fails marks them FAILED, and the apps stop showing
     * those trips as insured.
     */
    @Transactional
    public Result<InsurerReporter.Report, String> bordereau(LocalDate day) {
        Instant from = day.atStartOfDay(INDIA).toInstant();
        Instant until = day.plusDays(1).atStartOfDay(INDIA).toInstant();
        List<TripCoverageEntity> rows = coverages
                .findByCoverageStartedAtGreaterThanEqualAndCoverageStartedAtLessThanOrderByCoverageStartedAtAsc(from, until);
        Map<UUID, InsurancePolicyEntity> byId = new HashMap<>();
        List<InsurerReporter.Line> lines = rows.stream().map(c -> {
            InsurancePolicyEntity p = byId.computeIfAbsent(c.getPolicyId(), id -> policies.findById(id).orElse(null));
            return new InsurerReporter.Line(c.getBookingId(), p == null ? null : p.getInsurerName(),
                    p == null ? null : p.getMasterPolicyNumber(), c.getCoverageStartedAt(), c.getCoverageEndedAt(),
                    c.getCategory(), c.getPickupArea(), c.getDropArea(), c.getPremiumAmount());
        }).toList();
        InsurerReporter.Report report;
        try {
            report = reporter.report(day, lines);
        } catch (RuntimeException ex) {
            // Returned, not thrown: the FAILED marks must commit, so the apps
            // stop calling these trips insured and the console says why.
            log.error("Bordereau for {} could not be reported through {}: {}", day, reporter.name(), ex.getMessage());
            rows.stream().filter(c -> c.getReportedStatus() != TripCoverageEntity.ReportedStatus.REPORTED).forEach(c -> {
                c.markReportFailed();
                coverages.save(c);
            });
            return Result.failure(ex.getMessage());
        }
        Instant now = clock.instant();
        rows.stream().filter(c -> c.getReportedStatus() != TripCoverageEntity.ReportedStatus.REPORTED).forEach(c -> {
            c.markReported(now);
            coverages.save(c);
        });
        return Result.success(report);
    }

    public record PremiumRow(UUID policyId, PolicyKind kind, String insurerName, String policyNumber, long trips,
                             BigDecimal premium) {
    }

    /** What SheOut owes insurers in premiums for trips started in a month, per policy. A platform cost. */
    @Transactional(readOnly = true)
    public List<PremiumRow> premiumReport(YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(INDIA).toInstant();
        Instant until = month.plusMonths(1).atDay(1).atStartOfDay(INDIA).toInstant();
        return coverages.premiumByPolicy(from, until).stream().map(line -> {
            Optional<InsurancePolicyEntity> p = policies.findById(line.policyId());
            return new PremiumRow(line.policyId(), p.map(InsurancePolicyEntity::getKind).orElse(null),
                    p.map(InsurancePolicyEntity::getInsurerName).orElse(null),
                    p.map(InsurancePolicyEntity::getMasterPolicyNumber).orElse(null), line.trips(),
                    line.premium() == null ? BigDecimal.ZERO : line.premium());
        }).sorted(Comparator.comparing(PremiumRow::policyNumber, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
    }

    // -------------------------------------------------- operators: enrolments

    public enum EnrolmentError {
        NOT_FOUND,
        /** Marking ENROLLED needs the member id the insurer gave her. */
        MEMBER_ID_REQUIRED,
        /** Already exited; a new enrolment starts from her next verification. */
        ALREADY_EXITED
    }

    @Transactional(readOnly = true)
    public List<PartnerEnrolmentEntity> allEnrolments() {
        return enrolments.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Result<PartnerEnrolmentEntity, EnrolmentError> markEnrolled(UUID enrolmentId, String memberId) {
        Optional<PartnerEnrolmentEntity> found = enrolments.findById(enrolmentId);
        if (found.isEmpty()) {
            return Result.failure(EnrolmentError.NOT_FOUND);
        }
        if (blank(memberId)) {
            return Result.failure(EnrolmentError.MEMBER_ID_REQUIRED);
        }
        if (found.get().getStatus() == EnrolmentStatus.EXITED) {
            return Result.failure(EnrolmentError.ALREADY_EXITED);
        }
        found.get().enrol(memberId.trim(), today());
        return Result.success(enrolments.save(found.get()));
    }

    @Transactional
    public Result<PartnerEnrolmentEntity, EnrolmentError> markExited(UUID enrolmentId, String reason) {
        Optional<PartnerEnrolmentEntity> found = enrolments.findById(enrolmentId);
        if (found.isEmpty()) {
            return Result.failure(EnrolmentError.NOT_FOUND);
        }
        if (found.get().getStatus() == EnrolmentStatus.EXITED) {
            return Result.failure(EnrolmentError.ALREADY_EXITED);
        }
        found.get().exit(today(), blank(reason) ? "Exited by an operator" : reason.trim());
        return Result.success(enrolments.save(found.get()));
    }

    /** Joiners (enrolled in the month) and leavers (exited in the month), for the insurer's monthly list. */
    @Transactional(readOnly = true)
    public Movements movements(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        return new Movements(enrolments.findByEnrolledOnBetween(first, last), enrolments.findByExitedOnBetween(first, last));
    }

    public record Movements(List<PartnerEnrolmentEntity> joiners, List<PartnerEnrolmentEntity> leavers) {
    }

    @Transactional(readOnly = true)
    public Optional<InsurancePolicyEntity> policy(UUID id) {
        return policies.findById(id);
    }

    // ---------------------------------------------------------------- helpers

    /** The policy of this kind in force today, if any. */
    Optional<InsurancePolicyEntity> inForce(PolicyKind kind) {
        LocalDate today = today();
        return policies.findByKindAndActiveTrue(kind).stream().filter(p -> p.inForceOn(today))
                .max(Comparator.comparing(InsurancePolicyEntity::getEffectiveFrom));
    }

    private static CoverSummary summary(InsurancePolicyEntity p, String memberId) {
        return new CoverSummary(p.getKind(), p.getInsurerName(), p.getMasterPolicyNumber(), p.getSumInsured(),
                p.getCoverageSummary(), p.getClaimSteps(), p.getClaimsPhone(), p.getClaimsUrl(), p.getPolicySummaryUrl(),
                p.getEffectiveTo(), memberId);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }
}
