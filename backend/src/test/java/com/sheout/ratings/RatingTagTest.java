package com.sheout.ratings;

import com.sheout.auth.AccountRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RatingTagTest {

    @Test
    void eachSideOfTheTripIsOfferedItsOwnTags() {
        List<RatingTag> riderSays = RatingTag.offeredTo(AccountRole.CUSTOMER, 1);
        List<RatingTag> partnerSays = RatingTag.offeredTo(AccountRole.DRIVER, 1);

        assertThat(riderSays).contains(RatingTag.VEHICLE_MISMATCH).doesNotContain(RatingTag.RIDER_LATE);
        assertThat(partnerSays).contains(RatingTag.RIDER_LATE).doesNotContain(RatingTag.VEHICLE_MISMATCH);
    }

    @Test
    void aGoodRatingIsNeverOfferedAComplaint() {
        assertThat(RatingTag.offeredTo(AccountRole.CUSTOMER, 5))
                .containsExactly(RatingTag.DRIVER_ON_TIME, RatingTag.DRIVER_FRIENDLY,
                        RatingTag.CLEAN_VEHICLE, RatingTag.GOOD_ROUTE);
        assertThat(RatingTag.offeredTo(AccountRole.CUSTOMER, 4))
                .noneMatch(tag -> tag.sentiment() == RatingTag.Sentiment.NEGATIVE);
        assertThat(RatingTag.offeredTo(AccountRole.CUSTOMER, 3))
                .noneMatch(tag -> tag.sentiment() == RatingTag.Sentiment.POSITIVE);
    }

    @Test
    void everyOfferedListIsShortEnoughToRead() {
        for (AccountRole role : List.of(AccountRole.CUSTOMER, AccountRole.DRIVER)) {
            for (int stars : new int[]{1, 5}) {
                assertThat(RatingTag.offeredTo(role, stars)).hasSizeBetween(4, 5);
            }
        }
    }

    @Test
    void aTagFromTheWrongSideOrTheWrongStarsIsNotAllowed() {
        assertThat(RatingTag.UNSAFE_DRIVING.allowedWith(AccountRole.CUSTOMER, 2)).isTrue();
        // Sent with five stars, which is where a mis-tap would land in somebody's record.
        assertThat(RatingTag.UNSAFE_DRIVING.allowedWith(AccountRole.CUSTOMER, 5)).isFalse();
        // A partner cannot say her rider's vehicle was wrong.
        assertThat(RatingTag.UNSAFE_DRIVING.allowedWith(AccountRole.DRIVER, 2)).isFalse();
        assertThat(RatingTag.RIDER_FRIENDLY.allowedWith(AccountRole.DRIVER, 5)).isTrue();
    }

    @Test
    void anAdminIsOfferedNothingToTap() {
        assertThat(RatingTag.offeredTo(AccountRole.ADMIN, 5)).isEmpty();
        assertThat(RatingTag.offeredTo(AccountRole.ADMIN, 1)).isEmpty();
    }
}
