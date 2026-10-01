package com.sheout.driververification.internal;

import com.sheout.driververification.SelfiePrompt;
import com.sheout.driververification.ShiftCheckApi;
import com.sheout.driververification.ShiftCheckNeedsReview;
import com.sheout.driververification.ShiftCheckState;
import com.sheout.driververification.VerificationStatus;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.storage.DocumentRules;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The start-of-shift safety check - see ShiftCheckApi for why it exists.
 * <p>
 * THE FLOW. She asks for a challenge and gets one random prompt, the
 * address of the selfie she was verified with, and ten minutes. Her phone
 * takes the live selfie, compares the two faces itself, and sends the photo
 * with the distance between them. This class decides what the distance
 * means - the threshold lives here, not in the app, so it can be tuned
 * without shipping a new build.
 * <p>
 * THE OUTCOMES.
 * <ul>
 *   <li>A match passes, for {@code valid-hours}.</li>
 *   <li>No face in the frame is a retry that costs nothing. A visor down or
 *       a thumb over the lens is not somebody else.</li>
 *   <li>A face that is not hers is a retry too - bad light and a new
 *       haircut both do that - but the photo is kept, and the third one in a
 *       row sends the check to an operator. Until somebody looks, she cannot
 *       go online. That is the Uber rule and it is right here: the case it
 *       catches is a man on a women-only service.</li>
 *   <li>When her phone cannot compare at all (an old phone, a model that
 *       would not download) or there is no verified selfie to compare with
 *       (accounts from before selfies), it passes on the photo alone and is
 *       listed for an operator to glance at. Keeping somebody off work
 *       because our download failed would be punishing her for our bug.</li>
 * </ul>
 * <p>
 * Nothing here guesses gender from a face. Whether she is a woman was
 * settled by a person reading her ID; this checks she is that person.
 */
@Service
public class ShiftCheckService implements ShiftCheckApi {

    private static final Logger log = LoggerFactory.getLogger(ShiftCheckService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    /** Photos of checks that settled long ago are deleted - see housekeeping. */
    private static final Duration PHOTO_RETENTION = Duration.ofDays(30);
    private static final EnumSet<ShiftCheckEntity.Status> SETTLED = EnumSet.of(
            ShiftCheckEntity.Status.PASSED, ShiftCheckEntity.Status.CLEARED,
            ShiftCheckEntity.Status.REJECTED, ShiftCheckEntity.Status.RETRY);

    private final ShiftCheckRepository checks;
    private final VerificationRecordRepository records;
    private final DocumentStorage documentStorage;
    private final DomainEventPublisher eventPublisher;
    private final boolean enabled;
    private final Duration validFor;
    private final double matchThreshold;
    private final int reviewAfterMisses;
    /** Null when built by hand in a unit test - the phone's result stands. */
    private final ServerFaceCheck serverFaceCheck;
    private final double serverMinSimilarity;

    @org.springframework.beans.factory.annotation.Autowired
    public ShiftCheckService(ShiftCheckRepository checks,
                             VerificationRecordRepository records,
                             DocumentStorage documentStorage,
                             DomainEventPublisher eventPublisher,
                             @Value("${sheout.shift-check.enabled:true}") boolean enabled,
                             @Value("${sheout.shift-check.valid-hours:12}") long validHours,
                             @Value("${sheout.shift-check.match-threshold:0.55}") double matchThreshold,
                             @Value("${sheout.shift-check.review-after-misses:3}") int reviewAfterMisses,
                             ServerFaceCheck serverFaceCheck,
                             // Rekognition's 0-100 similarity. 90 is its own recommended
                             // line for identity checks; below it is not the same woman.
                             @Value("${sheout.face-check.min-similarity:90}") double serverMinSimilarity) {
        this.checks = checks;
        this.records = records;
        this.documentStorage = documentStorage;
        this.eventPublisher = eventPublisher;
        this.enabled = enabled;
        this.validFor = Duration.ofHours(validHours);
        this.matchThreshold = matchThreshold;
        this.reviewAfterMisses = reviewAfterMisses;
        this.serverFaceCheck = serverFaceCheck;
        this.serverMinSimilarity = serverMinSimilarity;
    }

    public ShiftCheckService(ShiftCheckRepository checks,
                             VerificationRecordRepository records,
                             DocumentStorage documentStorage,
                             DomainEventPublisher eventPublisher,
                             @Value("${sheout.shift-check.enabled:true}") boolean enabled,
                             // A working day. Long enough that nobody is asked twice in one
                             // shift; short enough that yesterday's check never covers today.
                             @Value("${sheout.shift-check.valid-hours:12}") long validHours,
                             // Euclidean distance between two 128-number face descriptors.
                             // 0.6 is the library's usual line between people; 0.55 is a
                             // little stricter, because the reference is a selfie from the
                             // same phone camera and a false pass matters more than a retry.
                             @Value("${sheout.shift-check.match-threshold:0.55}") double matchThreshold,
                             @Value("${sheout.shift-check.review-after-misses:3}") int reviewAfterMisses) {
        this.checks = checks;
        this.records = records;
        this.documentStorage = documentStorage;
        this.eventPublisher = eventPublisher;
        this.enabled = enabled;
        this.validFor = Duration.ofHours(validHours);
        this.matchThreshold = matchThreshold;
        this.reviewAfterMisses = reviewAfterMisses;
        this.serverFaceCheck = null;
        this.serverMinSimilarity = 90;
    }

    public boolean enabled() {
        return enabled;
    }

    public record Challenge(String challengeId, List<SelfiePrompt> prompts, Instant expiresAt,
                            String referenceSelfieUrl) {
    }

    /** What her own screen is told: the state, and what happened to her last try. */
    public record Status(boolean required, boolean valid, boolean underReview, Instant checkedAt,
                         Instant validUntil, boolean faceMatched, boolean helmetPhotoOnFile,
                         String lastResult, int missesBeforeReview) {
    }

    /** What the operator sees for one check. */
    public record ReviewItem(UUID checkId, UUID accountId, String status, String faceResult, Double faceDistance,
                             Instant submittedAt, String selfieUrl, String framesUrl, String helmetUrl,
                             String referenceSelfieUrl, List<SelfiePrompt> prompts,
                             /** The server's 0-100 face similarity, when it compared too; see ServerFaceCheck. */
                             Double serverSimilarity) {
    }

    /**
     * One prompt, not two: this is a check she does every working day, and
     * one turn of the head is enough to show a person is there.
     */
    @Transactional
    public Result<Challenge, ShiftCheckError> issueChallenge(UUID accountId) {
        if (latestAnswered(accountId).filter(c -> c.getStatus() == ShiftCheckEntity.Status.NEEDS_REVIEW).isPresent()) {
            return Result.failure(ShiftCheckError.UNDER_REVIEW);
        }
        List<SelfiePrompt> pool = new ArrayList<>(List.of(SelfiePrompt.values()));
        Collections.shuffle(pool, RANDOM);
        List<SelfiePrompt> prompts = List.of(pool.get(0));
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String challengeId = HexFormat.of().formatHex(nonce);
        Instant expiresAt = Instant.now().plus(CHALLENGE_TTL);
        checks.save(new ShiftCheckEntity(accountId, challengeId, prompts.get(0).name(), expiresAt));
        return Result.success(new Challenge(challengeId, prompts, expiresAt, referenceSelfieUrl(accountId).orElse(null)));
    }

    /**
     * Her answer.
     *
     * @param faceDistance what her phone measured, or null when it could not
     * @param faceOutcome  why there is no distance: NO_FACE, or anything else for "could not compare"
     */
    @Transactional
    public Result<Status, ShiftCheckError> submit(UUID accountId, String challengeId,
                                                  DocumentUpload selfie, DocumentUpload frames, DocumentUpload helmet,
                                                  Double faceDistance, String faceOutcome) {
        Instant now = Instant.now();
        Optional<ShiftCheckEntity> found = challengeId == null ? Optional.empty()
                : checks.findByChallengeIdAndAccountId(challengeId, accountId);
        if (found.isEmpty() || !found.get().answerable(now)) {
            return Result.failure(ShiftCheckError.CHALLENGE_EXPIRED);
        }
        if (latestAnswered(accountId).filter(c -> c.getStatus() == ShiftCheckEntity.Status.NEEDS_REVIEW).isPresent()) {
            return Result.failure(ShiftCheckError.UNDER_REVIEW);
        }
        if (!isPhoto(selfie) || !isPhoto(frames) || (helmet != null && !isPhoto(helmet))) {
            return Result.failure(ShiftCheckError.PHOTO_REQUIRED);
        }
        ShiftCheckEntity check = found.get();

        boolean hasReference = referenceKey(accountId).isPresent();
        ShiftCheckEntity.FaceResult face;
        Double distance = null;
        if (hasReference && faceDistance != null && Double.isFinite(faceDistance) && faceDistance >= 0) {
            distance = faceDistance;
            face = faceDistance <= matchThreshold ? ShiftCheckEntity.FaceResult.MATCH : ShiftCheckEntity.FaceResult.NO_MATCH;
        } else if (hasReference && "NO_FACE".equals(faceOutcome)) {
            face = ShiftCheckEntity.FaceResult.NO_FACE;
        } else {
            face = ShiftCheckEntity.FaceResult.UNAVAILABLE;
        }

        // The phone's verdict is a claim; when the server can check it, the
        // server decides. See ServerFaceCheck - empty means no opinion, and
        // the phone's result stands as before.
        Double serverSimilarity = null;
        if (hasReference && serverFaceCheck != null && face != ShiftCheckEntity.FaceResult.NO_FACE) {
            Optional<byte[]> reference = referenceKey(accountId).flatMap(documentStorage::load);
            Optional<Double> similarity = reference.flatMap(ref -> serverFaceCheck.similarity(ref, selfie.content()));
            if (similarity.isPresent()) {
                serverSimilarity = similarity.get();
                ShiftCheckEntity.FaceResult serverVerdict = serverSimilarity >= serverMinSimilarity
                        ? ShiftCheckEntity.FaceResult.MATCH : ShiftCheckEntity.FaceResult.NO_MATCH;
                if (serverVerdict != face && face != ShiftCheckEntity.FaceResult.UNAVAILABLE) {
                    log.warn("Shift check for {}: phone said {}, server similarity {} says {}", accountId, face,
                            Math.round(serverSimilarity), serverVerdict);
                }
                face = serverVerdict;
            }
        }

        if (face == ShiftCheckEntity.FaceResult.NO_FACE) {
            // Nothing worth keeping: a frame with no face in it is evidence of nothing.
            check.recordAnswer(ShiftCheckEntity.Status.RETRY, face, null, null, null, null, now);
            checks.save(check);
            return Result.success(statusFor(accountId, face.name()));
        }

        String selfieKey;
        String framesKey;
        String helmetKey = null;
        try {
            selfieKey = documentStorage.store(accountId, "shift-selfie", DocumentRules.asDetected(selfie));
            framesKey = documentStorage.store(accountId, "shift-liveness", DocumentRules.asDetected(frames));
            if (helmet != null) {
                helmetKey = documentStorage.store(accountId, "shift-helmet", DocumentRules.asDetected(helmet));
            }
        } catch (RuntimeException ex) {
            return Result.failure(ShiftCheckError.STORAGE_FAILED);
        }

        ShiftCheckEntity.Status outcome;
        if (face == ShiftCheckEntity.FaceResult.NO_MATCH) {
            int misses = 1 + consecutiveMisses(accountId);
            outcome = misses >= reviewAfterMisses ? ShiftCheckEntity.Status.NEEDS_REVIEW : ShiftCheckEntity.Status.RETRY;
        } else {
            outcome = ShiftCheckEntity.Status.PASSED;
        }
        check.recordAnswer(outcome, face, distance, selfieKey, framesKey, helmetKey, now);
        check.recordServerSimilarity(serverSimilarity);
        checks.save(check);

        if (outcome == ShiftCheckEntity.Status.NEEDS_REVIEW) {
            log.warn("Shift check for {} did not match {} times running - held for review", accountId, reviewAfterMisses);
            eventPublisher.publish(new ShiftCheckNeedsReview(accountId));
        }
        if (outcome == ShiftCheckEntity.Status.PASSED) {
            housekeeping(accountId, now);
        }
        return Result.success(statusFor(accountId, face.name()));
    }

    public Status statusFor(UUID accountId) {
        return statusFor(accountId, latestAnswered(accountId).map(c -> c.getFaceResult() == null ? null : c.getFaceResult().name()).orElse(null));
    }

    private Status statusFor(UUID accountId, String lastResult) {
        ShiftCheckState state = stateFor(accountId);
        int misses = consecutiveMisses(accountId);
        return new Status(enabled, state.valid(), state.underReview(), state.checkedAt(), state.validUntil(),
                state.faceMatched(), state.helmetPhotoOnFile(), lastResult, Math.max(0, reviewAfterMisses - misses));
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftCheckState stateFor(UUID accountId) {
        if (!enabled) {
            return ShiftCheckState.notRequired();
        }
        Instant now = Instant.now();
        List<ShiftCheckEntity> recent = checks.findAnsweredByAccount(accountId, PageRequest.of(0, 20));
        for (ShiftCheckEntity check : recent) {
            switch (check.getStatus()) {
                case NEEDS_REVIEW -> {
                    return new ShiftCheckState(false, true, null, null, false, false);
                }
                case PASSED, CLEARED -> {
                    Instant from = check.getStatus() == ShiftCheckEntity.Status.CLEARED && check.getReviewedAt() != null
                            ? check.getReviewedAt() : check.getSubmittedAt();
                    Instant until = from.plus(validFor);
                    boolean valid = now.isBefore(until);
                    return new ShiftCheckState(valid, false, from, until,
                            valid && check.getFaceResult() == ShiftCheckEntity.FaceResult.MATCH,
                            valid && check.getHelmetKey() != null);
                }
                case REJECTED -> {
                    // An operator said this one was not good enough: nothing
                    // older can stand in for it.
                    return new ShiftCheckState(false, false, null, null, false, false);
                }
                default -> {
                    // A retry says nothing on its own - keep looking for the
                    // last check that decided something.
                }
            }
        }
        return new ShiftCheckState(false, false, null, null, false, false);
    }

    /** Checks an operator should look at: held ones first, then passes nothing could compare. */
    @Transactional(readOnly = true)
    public List<ReviewItem> reviewQueue() {
        List<ReviewItem> items = new ArrayList<>();
        checks.findByStatusInOrderBySubmittedAtAsc(EnumSet.of(ShiftCheckEntity.Status.NEEDS_REVIEW))
                .forEach(c -> items.add(toReviewItem(c)));
        checks.findUnmatchedPasses(ShiftCheckEntity.Status.PASSED, ShiftCheckEntity.FaceResult.UNAVAILABLE,
                        Instant.now().minus(Duration.ofDays(7)))
                .forEach(c -> items.add(toReviewItem(c)));
        return items;
    }

    /**
     * CLEAR: it is her. A held check counts as passed from now; a pass that
     * was only on the photo is marked as looked at. REJECT: it is not good
     * enough - she has to take a new one, and it cannot be the photo alone
     * that gets her back on the road. Anything worse is an account block,
     * which is the console's existing tool for it.
     */
    @Transactional
    public Result<ReviewItem, ShiftCheckError> review(UUID checkId, UUID adminId, String decision, String note) {
        Optional<ShiftCheckEntity> found = checks.findById(checkId);
        if (found.isEmpty()) {
            return Result.failure(ShiftCheckError.NOT_FOUND);
        }
        ShiftCheckEntity check = found.get();
        boolean held = check.getStatus() == ShiftCheckEntity.Status.NEEDS_REVIEW;
        boolean auditable = check.getStatus() == ShiftCheckEntity.Status.PASSED && check.getReviewedAt() == null;
        if (!held && !auditable) {
            return Result.failure(ShiftCheckError.NOT_REVIEWABLE);
        }
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        Instant now = Instant.now();
        switch (decision == null ? "" : decision) {
            case "CLEAR" -> check.recordReview(held ? ShiftCheckEntity.Status.CLEARED : ShiftCheckEntity.Status.PASSED,
                    adminId, trimmed, now);
            case "REJECT" -> check.recordReview(ShiftCheckEntity.Status.REJECTED, adminId, trimmed, now);
            default -> {
                return Result.failure(ShiftCheckError.INVALID_DECISION);
            }
        }
        checks.save(check);
        return Result.success(toReviewItem(check));
    }

    /**
     * Account deletion: every photo goes. The rows stay with their outcomes,
     * the same as the verification record - whether the partner on a past
     * trip had passed her check is part of that trip's record, her face is not.
     */
    @Transactional
    public void deletePhotosFor(UUID accountId) {
        for (ShiftCheckEntity check : checks.findByAccountId(accountId)) {
            deletePhotos(check);
            checks.save(check);
        }
    }

    private ReviewItem toReviewItem(ShiftCheckEntity c) {
        return new ReviewItem(c.getId(), c.getAccountId(), c.getStatus().name(),
                c.getFaceResult() == null ? null : c.getFaceResult().name(), c.getFaceDistance(), c.getSubmittedAt(),
                url(c.getSelfieKey()), url(c.getFramesKey()), url(c.getHelmetKey()),
                referenceSelfieUrl(c.getAccountId()).orElse(null),
                c.getPrompts() == null ? List.of() : List.of(SelfiePrompt.valueOf(c.getPrompts())),
                c.getServerSimilarity());
    }

    /** Misses since the last check that settled anything. */
    private int consecutiveMisses(UUID accountId) {
        int misses = 0;
        for (ShiftCheckEntity check : checks.findAnsweredByAccount(accountId, PageRequest.of(0, 20))) {
            if (check.getStatus() == ShiftCheckEntity.Status.RETRY) {
                if (check.getFaceResult() == ShiftCheckEntity.FaceResult.NO_MATCH) misses++;
                continue;
            }
            break;
        }
        return misses;
    }

    private Optional<ShiftCheckEntity> latestAnswered(UUID accountId) {
        return checks.findAnsweredByAccount(accountId, PageRequest.of(0, 1)).stream().findFirst();
    }

    /** Her verified selfie - the one a person compared with her ID. */
    private Optional<String> referenceKey(UUID accountId) {
        return records.findByAccountId(accountId)
                .filter(r -> r.getGenderVerificationStatus() == VerificationStatus.VERIFIED)
                .map(VerificationRecordEntity::getSelfieDocumentKey);
    }

    private Optional<String> referenceSelfieUrl(UUID accountId) {
        return referenceKey(accountId).map(documentStorage::resolveUrl);
    }

    private String url(String key) {
        return key == null ? null : documentStorage.resolveUrl(key);
    }

    /** Photos from checks that settled more than PHOTO_RETENTION ago go; the outcomes stay. */
    private void housekeeping(UUID accountId, Instant now) {
        for (ShiftCheckEntity old : checks.findExpiredPhotos(accountId, now.minus(PHOTO_RETENTION), SETTLED)) {
            deletePhotos(old);
            checks.save(old);
        }
    }

    private void deletePhotos(ShiftCheckEntity check) {
        for (String key : new String[] {check.getSelfieKey(), check.getFramesKey(), check.getHelmetKey()}) {
            if (key == null) continue;
            try {
                documentStorage.delete(key);
            } catch (RuntimeException ex) {
                log.warn("Could not delete shift check photo {}: {}", key, ex.getMessage());
            }
        }
        check.clearPhotos();
    }

    private static boolean isPhoto(DocumentUpload upload) {
        return upload != null && DocumentRules.check(upload) == null && DocumentRules.photoTypeOf(upload.content()) != null;
    }
}
