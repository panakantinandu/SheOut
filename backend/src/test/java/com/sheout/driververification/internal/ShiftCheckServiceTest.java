package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.ShiftCheckNeedsReview;
import com.sheout.driververification.ShiftCheckState;
import com.sheout.driververification.VerificationStatus;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a start-of-shift selfie decides: a match lets her work, a face that
 * is not hers is a retry until it is the third, and our own failure to
 * compare never keeps her off the road.
 */
class ShiftCheckServiceTest {

    private final UUID partner = UUID.randomUUID();
    private final List<ShiftCheckEntity> rows = new ArrayList<>();
    private ShiftCheckRepository checks;
    private VerificationRecordRepository records;
    private DomainEventPublisher events;
    private DocumentUpload photo;

    @BeforeEach
    void setUp() throws IOException {
        checks = mock(ShiftCheckRepository.class);
        when(checks.save(any())).thenAnswer(call -> {
            ShiftCheckEntity row = call.getArgument(0);
            if (!rows.contains(row)) rows.add(row);
            return row;
        });
        when(checks.findByChallengeIdAndAccountId(anyString(), any())).thenAnswer(call -> rows.stream()
                .filter(r -> r.getAccountId().equals(call.getArgument(1)))
                .filter(r -> challengeOf(r).equals(call.getArgument(0)))
                .findFirst());
        when(checks.findAnsweredByAccount(any(), any(Pageable.class))).thenAnswer(call -> {
            Pageable page = call.getArgument(1);
            return rows.stream()
                    .filter(r -> r.getAccountId().equals(call.getArgument(0)) && r.getSubmittedAt() != null)
                    .sorted(Comparator.comparing(ShiftCheckEntity::getSubmittedAt).reversed()
                            .thenComparing(r -> -rows.indexOf(r)))
                    .limit(page.getPageSize())
                    .toList();
        });
        records = mock(VerificationRecordRepository.class);
        events = mock(DomainEventPublisher.class);
        photo = png();
    }

    private final java.util.Map<ShiftCheckEntity, String> challengeIds = new java.util.IdentityHashMap<>();

    private String challengeOf(ShiftCheckEntity row) {
        return challengeIds.getOrDefault(row, "");
    }

    private ShiftCheckService service(long validHours) {
        DocumentStorage storage = mock(DocumentStorage.class);
        when(storage.store(any(), anyString(), any())).thenAnswer(call -> "key-" + UUID.randomUUID());
        when(storage.resolveUrl(anyString())).thenAnswer(call -> "https://docs/" + call.getArgument(0));
        return new ShiftCheckService(checks, records, storage, events, true, validHours, 0.55, 3);
    }

    /** The same service with the server comparing faces too, answering `similarity` (empty: no opinion). */
    private ShiftCheckService serviceWithServerCheck(Optional<Double> similarity) {
        DocumentStorage storage = mock(DocumentStorage.class);
        when(storage.store(any(), anyString(), any())).thenAnswer(call -> "key-" + UUID.randomUUID());
        when(storage.resolveUrl(anyString())).thenAnswer(call -> "https://docs/" + call.getArgument(0));
        when(storage.load("selfie-on-file")).thenReturn(Optional.of(new byte[]{1, 2, 3}));
        ServerFaceCheck server = mock(ServerFaceCheck.class);
        when(server.similarity(any(), any())).thenReturn(similarity);
        return new ShiftCheckService(checks, records, storage, events, true, 12, 0.55, 3, server, 90);
    }

    /** A tampered phone reporting a match for somebody else is overruled. */
    @Test
    void theServersVerdictWinsOverThePhones() {
        verifiedWithSelfie();
        ShiftCheckService service = serviceWithServerCheck(Optional.of(40.0));

        ShiftCheckService.Status status = attempt(service, 0.10, null, false);

        assertThat(status.valid()).isFalse();
        assertThat(rows.get(rows.size() - 1).getFaceResult()).isEqualTo(ShiftCheckEntity.FaceResult.NO_MATCH);
        assertThat(rows.get(rows.size() - 1).getServerSimilarity()).isEqualTo(40.0);
    }

    @Test
    void theServerCanConfirmWhatThePhoneCouldNotCheck() {
        verifiedWithSelfie();
        ShiftCheckService service = serviceWithServerCheck(Optional.of(97.0));

        ShiftCheckService.Status status = attempt(service, null, "MODEL_FAILED", false);

        assertThat(status.valid()).isTrue();
        assertThat(rows.get(rows.size() - 1).getFaceResult()).isEqualTo(ShiftCheckEntity.FaceResult.MATCH);
    }

    @Test
    void noServerOpinionLeavesThePhonesResult() {
        verifiedWithSelfie();
        ShiftCheckService service = serviceWithServerCheck(Optional.empty());

        assertThat(attempt(service, 0.31, null, false).valid()).isTrue();
        assertThat(rows.get(rows.size() - 1).getServerSimilarity()).isNull();
    }

    private void verifiedWithSelfie() {
        VerificationRecordEntity record = new VerificationRecordEntity(partner, AccountRole.DRIVER);
        record.setGenderVerificationStatus(VerificationStatus.VERIFIED);
        record.recordLiveSelfie("selfie-on-file", "frames-on-file", "SMILE,TURN_LEFT", java.time.Instant.now());
        when(records.findByAccountId(partner)).thenReturn(Optional.of(record));
    }

    /** Issues a challenge and answers it. */
    private ShiftCheckService.Status attempt(ShiftCheckService service, Double distance, String outcome, boolean helmet) {
        String id = service.issueChallenge(partner).value().challengeId();
        challengeIds.put(rows.get(rows.size() - 1), id);
        return service.submit(partner, id, photo, photo, helmet ? photo : null, distance, outcome).value();
    }

    @Test
    void aMatchLetsHerWorkAndSaysSo() {
        verifiedWithSelfie();
        ShiftCheckService service = service(12);

        ShiftCheckService.Status status = attempt(service, 0.31, null, true);

        assertThat(status.valid()).isTrue();
        ShiftCheckState state = service.stateFor(partner);
        assertThat(state.valid()).isTrue();
        assertThat(state.faceMatched()).isTrue();
        assertThat(state.helmetPhotoOnFile()).isTrue();
        assertThat(state.validUntil()).isAfter(state.checkedAt());
    }

    @Test
    void aCheckOnlyLastsItsWindow() {
        verifiedWithSelfie();
        ShiftCheckService service = service(0);

        attempt(service, 0.31, null, false);

        assertThat(service.stateFor(partner).valid()).isFalse();
    }

    @Test
    void theThirdMismatchInARowHoldsHerForReview() {
        verifiedWithSelfie();
        ShiftCheckService service = service(12);

        assertThat(attempt(service, 0.8, null, false).missesBeforeReview()).isEqualTo(2);
        assertThat(attempt(service, 0.8, null, false).valid()).isFalse();
        verify(events, never()).publish(any(ShiftCheckNeedsReview.class));

        ShiftCheckService.Status third = attempt(service, 0.8, null, false);

        assertThat(third.underReview()).isTrue();
        verify(events).publish(any(ShiftCheckNeedsReview.class));
        // Held means held: no new prompts until an operator looks.
        assertThat(service.issueChallenge(partner).isFailure()).isTrue();
    }

    @Test
    void noFaceInTheFrameIsAFreeRetry() {
        verifiedWithSelfie();
        ShiftCheckService service = service(12);

        for (int i = 0; i < 5; i++) {
            attempt(service, null, "NO_FACE", false);
        }

        assertThat(service.stateFor(partner).underReview()).isFalse();
        assertThat(attempt(service, 0.2, null, false).valid()).isTrue();
    }

    @Test
    void whenNothingCanCompareSheStillWorksButItIsNotCalledAMatch() {
        // No verified selfie on file: an account from before selfies.
        when(records.findByAccountId(partner)).thenReturn(Optional.empty());
        ShiftCheckService service = service(12);

        // A distance sent anyway means nothing without a reference.
        attempt(service, 0.1, null, false);

        ShiftCheckState state = service.stateFor(partner);
        assertThat(state.valid()).isTrue();
        assertThat(state.faceMatched()).isFalse();
    }

    @Test
    void anOperatorClearingAHeldCheckLetsHerWork() {
        verifiedWithSelfie();
        ShiftCheckService service = service(12);
        attempt(service, 0.9, null, false);
        attempt(service, 0.9, null, false);
        attempt(service, 0.9, null, false);
        ShiftCheckEntity held = rows.get(rows.size() - 1);
        when(checks.findById(any())).thenReturn(Optional.of(held));

        assertThat(service.review(UUID.randomUUID(), UUID.randomUUID(), "CLEAR", "Same person, poor light").isSuccess()).isTrue();

        assertThat(service.stateFor(partner).valid()).isTrue();
        assertThat(service.stateFor(partner).underReview()).isFalse();
    }

    @Test
    void switchedOffItAsksNothing() {
        ShiftCheckService off = new ShiftCheckService(checks, records, mock(DocumentStorage.class), events, false, 12, 0.55, 3);

        assertThat(off.stateFor(partner).valid()).isTrue();
    }

    private static DocumentUpload png() throws IOException {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(7);
        for (int y = 0; y < 480; y++) {
            for (int x = 0; x < 640; x++) {
                image.setRGB(x, y, random.nextInt());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new DocumentUpload("selfie.png", "image/png", out.toByteArray());
    }
}
