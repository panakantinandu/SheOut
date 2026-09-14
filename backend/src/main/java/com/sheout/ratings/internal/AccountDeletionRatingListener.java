package com.sheout.ratings.internal;

import com.sheout.privacy.AccountDeletionRequested;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes the comments a deleted account wrote. See RatingEntity.eraseComment for why the stars stay. */
@Component
class AccountDeletionRatingListener {

    private final RatingRepository ratings;

    AccountDeletionRatingListener(RatingRepository ratings) {
        this.ratings = ratings;
    }

    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        var written = ratings.findByRaterAccountIdAndCommentIsNotNull(event.accountId());
        written.forEach(RatingEntity::eraseComment);
        ratings.saveAll(written);
    }
}
