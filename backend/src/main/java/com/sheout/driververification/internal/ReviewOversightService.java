package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.VerificationCheck;
import com.sheout.sharedkernel.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checking the checkers: a weekly sample of decided cases for a second
 * look, and how often each reviewer overrides a failed check.
 * <p>
 * THE SAMPLE. Every account with a decision that week - a person approving
 * or sending back, or the software passing a required check - is in the
 * pool. One in ten is drawn, by a hash of the account and the week, so the
 * draw is random across accounts but the same every time anyone opens that
 * week: a sample that changed on reload could be re-rolled until it looked
 * clean. A week with any decisions always has at least one case.
 * <p>
 * Auto-passed cases are in the pool on purpose. Software that passes
 * somebody is exactly what a person should be spot-checking.
 * <p>
 * Weeks run Monday to Sunday in India, named like 2026-W41.
 */
@Service
public class ReviewOversightService {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final int SAMPLE_PERCENT = 10;
    private static final Set<String> APPROVALS = Set.of(VerificationAudit.ID_CHECK_VERIFIED,
            VerificationAudit.DOCUMENT_VERIFIED, VerificationAudit.POLICE_VERIFIED);
    private static final Set<String> SENT_BACK = Set.of(VerificationAudit.ID_CHECK_REJECTED,
            VerificationAudit.DOCUMENT_REJECTED, VerificationAudit.POLICE_REJECTED);

    private final VerificationCheckRepository checks;
    private final VerificationRecordRepository records;
    private final VerificationSampleReviewRepository secondLooks;
    private final VerificationAuditRepository auditEvents;
    private final VerificationCheckService engine;
    private final VerificationAudit audit;
    private final com.sheout.staff.StaffDirectory staff;

    ReviewOversightService(VerificationCheckRepository checks, VerificationRecordRepository records,
                           VerificationSampleReviewRepository secondLooks, VerificationAuditRepository auditEvents,
                           VerificationCheckService engine, VerificationAudit audit, com.sheout.staff.StaffDirectory staff) {
        this.staff = staff;
        this.checks = checks;
        this.records = records;
        this.secondLooks = secondLooks;
        this.auditEvents = auditEvents;
        this.engine = engine;
        this.audit = audit;
    }

    public record SampleCase(UUID accountId, AccountRole role, boolean autoPassed, List<VerificationCheck> decided,
                             SecondLook secondLook) {
    }

    public record SecondLook(UUID reviewedBy, String outcome, String note, Instant at) {
    }

    /** staffNames: who decided and who looked, by account, as "Asha (Support agent)". */
    public record Sample(String week, int poolSize, List<SampleCase> cases, Map<UUID, String> staffNames) {
    }

    public record ReviewerStats(UUID reviewerId, String reviewer, long approvals, long sentBack, long overridesAsked,
                                long overridesSigned, double overrideRate) {
    }

    /** This week, in India. */
    public static String currentWeek() {
        return weekOf(LocalDate.now(INDIA));
    }

    static String weekOf(LocalDate day) {
        return String.format("%d-W%02d", day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Monday of that week; null for a week written any other way. For the console's other weekly counts. */
    public static LocalDate mondayOfWeek(String week) {
        return mondayOf(week);
    }

    /** Monday of that week; null for a week written any other way. */
    static LocalDate mondayOf(String week) {
        if (week == null || !week.matches("\\d{4}-W\\d{2}")) {
            return null;
        }
        try {
            LocalDate jan4 = LocalDate.of(Integer.parseInt(week.substring(0, 4)), 1, 4);
            int number = Integer.parseInt(week.substring(6));
            if (number < 1 || number > 53) {
                return null;
            }
            return jan4.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(number - 1L);
        } catch (DateTimeParseException | NumberFormatException ex) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Result<Sample, VerificationError> weeklySample(String week) {
        LocalDate monday = mondayOf(week);
        if (monday == null) {
            return Result.failure(VerificationError.INVALID_WEEK);
        }
        Instant from = monday.atStartOfDay(INDIA).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(INDIA).toInstant();
        Map<UUID, List<VerificationCheckEntity>> pool = new LinkedHashMap<>();
        for (VerificationCheckEntity row : checks.findByDecidedAtBetween(from, to)) {
            boolean byPerson = VerificationAudit.ACTOR_OPERATOR.equals(row.getDecidedByRole());
            boolean autoPassed = row.getStatus() == CheckStatus.PASSED && row.isRequired();
            if (byPerson || autoPassed) {
                pool.computeIfAbsent(row.getAccountId(), k -> new ArrayList<>()).add(row);
            }
        }
        List<UUID> drawn = pool.keySet().stream().filter(id -> draw(id, week) < SAMPLE_PERCENT).toList();
        if (drawn.isEmpty() && !pool.isEmpty()) {
            drawn = List.of(pool.keySet().stream().min(Comparator.comparingInt(id -> draw(id, week))).orElseThrow());
        }
        Map<UUID, VerificationSampleReviewEntity> looked = new LinkedHashMap<>();
        secondLooks.findBySampleWeek(week).forEach(r -> looked.put(r.getAccountId(), r));
        List<SampleCase> cases = new ArrayList<>();
        for (UUID id : drawn) {
            List<VerificationCheckEntity> rows = pool.get(id);
            AccountRole role = records.findByAccountId(id).map(VerificationRecordEntity::getRole).orElse(null);
            VerificationSampleReviewEntity look = looked.get(id);
            cases.add(new SampleCase(id, role,
                    rows.stream().anyMatch(r -> r.getStatus() == CheckStatus.PASSED && r.isRequired()),
                    rows.stream().map(engine::toView).toList(),
                    look == null ? null : new SecondLook(look.getReviewedBy(), look.getOutcome(), look.getNote(), look.getCreatedAt())));
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        cases.forEach(c -> {
            c.decided().forEach(d -> name(names, d.decidedBy()));
            if (c.secondLook() != null) {
                name(names, c.secondLook().reviewedBy());
            }
        });
        return Result.success(new Sample(week, pool.size(), cases, names));
    }

    private void name(Map<UUID, String> names, UUID accountId) {
        if (accountId != null && !names.containsKey(accountId)) {
            names.put(accountId, staff.label(accountId).orElse("Former staff"));
        }
    }

    /**
     * A second reviewer's word on a sampled case: AGREE, or CONCERN with a
     * note. Never by somebody who decided any of that case's checks.
     */
    @Transactional
    public Result<SecondLook, VerificationError> recordSecondLook(UUID accountId, String week, UUID reviewer, String outcome,
                                                                 String note) {
        if (!"AGREE".equals(outcome) && !"CONCERN".equals(outcome)) {
            return Result.failure(VerificationError.INVALID_DECISION);
        }
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        if ("CONCERN".equals(outcome) && trimmed == null) {
            return Result.failure(VerificationError.REASON_REQUIRED);
        }
        Result<Sample, VerificationError> sample = weeklySample(week);
        if (sample.isFailure()) {
            return Result.failure(sample.error());
        }
        SampleCase found = sample.value().cases().stream().filter(c -> c.accountId().equals(accountId)).findFirst().orElse(null);
        if (found == null) {
            return Result.failure(VerificationError.NOT_IN_SAMPLE);
        }
        if (found.decided().stream().anyMatch(c -> reviewer.equals(c.decidedBy()))) {
            return Result.failure(VerificationError.NEEDS_SECOND_PERSON);
        }
        if (secondLooks.findByAccountIdAndSampleWeek(accountId, week).isPresent()) {
            return Result.failure(VerificationError.ALREADY_LOOKED);
        }
        VerificationSampleReviewEntity saved = secondLooks.save(new VerificationSampleReviewEntity(accountId, week, reviewer, outcome,
                trimmed == null ? null : trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed));
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.SAMPLE_SECOND_LOOK, null, null,
                week + ": " + outcome + (trimmed == null ? "" : ". " + trimmed));
        return Result.success(new SecondLook(reviewer, outcome, saved.getNote(), Instant.now()));
    }

    /**
     * Each reviewer's decisions and overrides over the last days, read from
     * the verification audit trail. overrideRate is overrides asked for per
     * decision made: a reviewer whose rate stands out is the one to sit with.
     */
    @Transactional(readOnly = true)
    public List<ReviewerStats> reviewerStats(int days) {
        Instant since = Instant.now().minus(Duration.ofDays(Math.max(1, Math.min(days, 365))));
        Map<UUID, long[]> counts = new LinkedHashMap<>();
        for (VerificationAuditEntity event : auditEvents.findByActorRoleAndAtAfter(VerificationAudit.ACTOR_OPERATOR, since)) {
            if (event.getActorId() == null) {
                continue;
            }
            long[] c = counts.computeIfAbsent(event.getActorId(), k -> new long[4]);
            String action = event.getAction();
            if (APPROVALS.contains(action)) {
                c[0]++;
            } else if (SENT_BACK.contains(action)) {
                c[1]++;
            } else if (VerificationAudit.CHECK_OVERRIDE_REQUESTED.equals(action)) {
                c[2]++;
            } else if (VerificationAudit.CHECK_OVERRIDDEN.equals(action)) {
                c[3]++;
            }
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue()[0] + e.getValue()[1] + e.getValue()[2] + e.getValue()[3] > 0)
                .map(e -> {
                    long[] c = e.getValue();
                    long decisions = c[0] + c[1];
                    return new ReviewerStats(e.getKey(), staff.label(e.getKey()).orElse("Former staff"), c[0], c[1], c[2], c[3],
                            decisions == 0 ? 0 : (double) c[2] / decisions);
                })
                .sorted(Comparator.comparingDouble(ReviewerStats::overrideRate).reversed())
                .toList();
    }

    /** 0-99, the same for the same account and week. */
    static int draw(UUID accountId, String week) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((week + ":" + accountId).getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xff) << 24) | ((hash[1] & 0xff) << 16) | ((hash[2] & 0xff) << 8) | (hash[3] & 0xff);
            return Math.floorMod(value, 100);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
