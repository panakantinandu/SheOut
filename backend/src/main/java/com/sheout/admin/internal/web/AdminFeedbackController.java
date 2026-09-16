package com.sheout.admin.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.ratings.AggregateRating;
import com.sheout.ratings.RatingTag;
import com.sheout.ratings.RatingTagCount;
import com.sheout.ratings.RatingsApi;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The console's Feedback section: what people have been tapping about an
 * account lately, counted.
 * <p>
 * It exists because reading comments does not scale and almost nobody leaves
 * one. Twelve "driver was late" against one partner in a month is a pattern
 * worth a conversation; the same twelve ratings as bare stars are a slightly
 * lower average nobody notices.
 * <p>
 * Counts only, and never who gave them - the same rule the public aggregate
 * follows, for the same reason: a partner able to work out which rider said
 * what is the thing that makes people afraid to say anything.
 * <p>
 * Read-only. Nothing here blocks, flags or scores anybody; the existing trust
 * review queue is where an account goes when a number crosses a threshold,
 * and this is for a person to read.
 */
@RestController
@RequestMapping("/api/v1/admin/feedback")
public class AdminFeedbackController {

    /** The default window an operator thinks in: "this month". */
    private static final int DEFAULT_DAYS = 30;

    /** A year back is as far as this is useful; beyond it the list is history, not a pattern. */
    private static final int MAX_DAYS = 365;

    /** Accounts per response, by how much has been said about them. Nobody reads past this. */
    private static final int MAX_ACCOUNTS = 100;

    private final RatingsApi ratingsApi;
    private final AuthApi authApi;
    private final DriverProfileApi driverProfileApi;
    private final CustomerProfileApi customerProfileApi;

    public AdminFeedbackController(RatingsApi ratingsApi, AuthApi authApi,
                                    DriverProfileApi driverProfileApi, CustomerProfileApi customerProfileApi) {
        this.ratingsApi = ratingsApi;
        this.authApi = authApi;
        this.driverProfileApi = driverProfileApi;
        this.customerProfileApi = customerProfileApi;
    }

    /**
     * Tag counts per account over the last {@code days}, most-tagged account
     * first.
     * <p>
     * {@code about} is the side being rated, which is the question an
     * operator actually asks - "what are riders saying about partners". The
     * rows it counts are the ones written by the other side.
     */
    @GetMapping
    public ResponseEntity<List<FeedbackRow>> feedback(
            @RequestParam(required = false, defaultValue = "DRIVER") AccountRole about,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_DAYS) int days) {
        requireAdmin();
        if (about != AccountRole.DRIVER && about != AccountRole.CUSTOMER) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Bad Request",
                    "Ratings are about partners or riders.");
        }
        int window = Math.min(Math.max(days, 1), MAX_DAYS);
        Instant since = Instant.now().minus(Duration.ofDays(window));

        // Rows written by the other side are the ones about this side.
        AccountRole raterRole = about == AccountRole.DRIVER ? AccountRole.CUSTOMER : AccountRole.DRIVER;
        List<RatingTagCount> counts = ratingsApi.countTagsSince(since, Set.of(raterRole));

        Map<UUID, List<RatingTagCount>> byAccount = new LinkedHashMap<>();
        for (RatingTagCount count : counts) {
            byAccount.computeIfAbsent(count.ratedAccountId(), id -> new ArrayList<>()).add(count);
        }

        Map<UUID, AggregateRating> aggregates = ratingsApi.getAggregateRatings(byAccount.keySet());
        List<FeedbackRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, List<RatingTagCount>> entry : byAccount.entrySet()) {
            UUID accountId = entry.getKey();
            List<TagCount> tags = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(RatingTagCount::count).reversed())
                    .map(c -> new TagCount(c.tag(), c.tag().label(), c.tag().sentiment(), c.count()))
                    .toList();
            AggregateRating aggregate = aggregates.getOrDefault(accountId, AggregateRating.none());
            rows.add(new FeedbackRow(
                    accountId,
                    about,
                    nameOf(accountId, about),
                    authApi.findAccount(accountId).map(AccountSummary::phoneNumber).orElse(null),
                    tags.stream().mapToLong(TagCount::count).sum(),
                    tags.stream().filter(t -> t.sentiment() == RatingTag.Sentiment.NEGATIVE)
                            .mapToLong(TagCount::count).sum(),
                    aggregate.averageStars(),
                    aggregate.totalRatings(),
                    window,
                    tags));
        }
        rows.sort(Comparator.comparingLong(FeedbackRow::totalTags).reversed());
        return ResponseEntity.ok(rows.size() > MAX_ACCOUNTS ? rows.subList(0, MAX_ACCOUNTS) : rows);
    }

    private String nameOf(UUID accountId, AccountRole role) {
        return role == AccountRole.DRIVER
                ? driverProfileApi.findByAccountId(accountId).map(p -> p.name()).orElse(null)
                : customerProfileApi.findByAccountId(accountId).map(p -> p.name()).orElse(null);
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }

    /**
     * One account and what has been said about it in the window.
     * negativeTags is carried separately so the console can sort and colour
     * by the half that means something is wrong.
     */
    public record FeedbackRow(UUID accountId, AccountRole role, String name, String phone,
                              long totalTags, long negativeTags,
                              Double averageStars, int totalRatings,
                              int windowDays, List<TagCount> tags) {
    }

    /** The label travels with the code, so the console never keeps its own copy of the wording. */
    public record TagCount(RatingTag tag, String label, RatingTag.Sentiment sentiment, long count) {
    }
}
