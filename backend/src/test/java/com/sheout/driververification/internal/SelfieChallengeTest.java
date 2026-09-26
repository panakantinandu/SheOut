package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A live selfie answers exactly one challenge: the latest, in time, once. */
class SelfieChallengeTest {

    private static final Instant NOW = Instant.parse("2026-09-26T06:00:00Z");

    private VerificationRecordEntity recordWithChallenge(String nonce) {
        VerificationRecordEntity record = new VerificationRecordEntity(UUID.randomUUID(), AccountRole.CUSTOMER);
        record.issueSelfieChallenge(nonce, "SMILE,TURN_LEFT", NOW.plusSeconds(900));
        return record;
    }

    @Test
    void theIssuedChallengeIsAnsweredOnceWithItsPrompts() {
        VerificationRecordEntity record = recordWithChallenge("abc");

        assertThat(record.consumeSelfieChallenge("abc", NOW)).contains("SMILE,TURN_LEFT");
        assertThat(record.consumeSelfieChallenge("abc", NOW)).isEmpty();
    }

    @Test
    void anotherIdAnswersNothing() {
        VerificationRecordEntity record = recordWithChallenge("abc");

        assertThat(record.consumeSelfieChallenge("xyz", NOW)).isEmpty();
        assertThat(record.consumeSelfieChallenge(null, NOW)).isEmpty();
        // A wrong guess does not use up the real one.
        assertThat(record.consumeSelfieChallenge("abc", NOW)).isPresent();
    }

    @Test
    void anExpiredChallengeIsRefused() {
        VerificationRecordEntity record = recordWithChallenge("abc");

        assertThat(record.consumeSelfieChallenge("abc", NOW.plusSeconds(901))).isEmpty();
    }

    @Test
    void aNewChallengeReplacesTheOldOne() {
        VerificationRecordEntity record = recordWithChallenge("first");
        record.issueSelfieChallenge("second", "LOOK_UP,SMILE", NOW.plusSeconds(900));

        assertThat(record.consumeSelfieChallenge("first", NOW)).isEmpty();
        assertThat(record.consumeSelfieChallenge("second", NOW)).contains("LOOK_UP,SMILE");
    }
}
