package com.sheout.ratings;

import com.sheout.auth.AccountRole;

import java.util.Arrays;
import java.util.List;

/**
 * The tappable reasons offered alongside the stars.
 * <p>
 * They exist because almost nobody writes a comment, and a bare three stars
 * tells an operator nothing about what to do. A tag is one tap, so people
 * actually give one, and twelve of the same tag against one account in a
 * month is a pattern a person can act on - which is the entire point.
 * <p>
 * WHICH TAGS ARE OFFERED depends on two things: who is rating, and how many
 * stars they gave. A rider rating her partner and a partner rating her rider
 * are not the same question - "vehicle didn't match" is meaningless in the
 * other direction - so each tag names the rater it belongs to. And a rating
 * of four or five stars is offered the good reasons only: showing "unsafe
 * driving" beside five stars invites a mis-tap that lands in somebody's
 * record forever.
 * <p>
 * The catalogue is served to both apps at GET /api/v1/ratings/tags rather
 * than hard-coded in them, so the labels an operator reads in the console
 * and the ones a rider taps are the same strings, and adding a tag is one
 * change here.
 * <p>
 * Enum names are stored in the database. Renaming a constant rewrites what
 * somebody said months ago; add a new one and stop offering the old instead.
 */
public enum RatingTag {

    // What a rider says about her partner.
    DRIVER_LATE("Driver was late", Sentiment.NEGATIVE, AccountRole.CUSTOMER),
    VEHICLE_MISMATCH("Vehicle didn't match details", Sentiment.NEGATIVE, AccountRole.CUSTOMER),
    UNUSUAL_ROUTE("Route seemed unusual", Sentiment.NEGATIVE, AccountRole.CUSTOMER),
    UNSAFE_DRIVING("Unsafe driving", Sentiment.NEGATIVE, AccountRole.CUSTOMER),
    // Where she was set down, not how she got there. Only ever showed up in
    // free-text complaints, where it could not be counted; as tags it can be
    // counted per partner and per drop point.
    DROPPED_WRONG_SIDE_OF_ROAD("Dropped on wrong side of road", Sentiment.NEGATIVE, AccountRole.CUSTOMER),
    HAD_TO_CROSS_TRAFFIC("Had to cross traffic to reach destination", Sentiment.NEGATIVE, AccountRole.CUSTOMER),
    CUSTOMER_OTHER("Other", Sentiment.NEGATIVE, AccountRole.CUSTOMER),
    DRIVER_ON_TIME("On time", Sentiment.POSITIVE, AccountRole.CUSTOMER),
    DRIVER_FRIENDLY("Friendly", Sentiment.POSITIVE, AccountRole.CUSTOMER),
    CLEAN_VEHICLE("Clean vehicle", Sentiment.POSITIVE, AccountRole.CUSTOMER),
    GOOD_ROUTE("Great route", Sentiment.POSITIVE, AccountRole.CUSTOMER),

    // What a partner says about her rider. Deliberately a different list:
    // offering her "unsafe driving" about somebody who was a passenger would
    // be nonsense, and nonsense options are how a tag count stops meaning
    // anything.
    RIDER_LATE("Kept me waiting", Sentiment.NEGATIVE, AccountRole.DRIVER),
    WRONG_PICKUP_POINT("Pickup point was wrong", Sentiment.NEGATIVE, AccountRole.DRIVER),
    RIDER_RUDE("Rude behaviour", Sentiment.NEGATIVE, AccountRole.DRIVER),
    FELT_UNSAFE("I did not feel safe", Sentiment.NEGATIVE, AccountRole.DRIVER),
    DRIVER_OTHER("Other", Sentiment.NEGATIVE, AccountRole.DRIVER),
    RIDER_ON_TIME("Ready on time", Sentiment.POSITIVE, AccountRole.DRIVER),
    RIDER_FRIENDLY("Friendly", Sentiment.POSITIVE, AccountRole.DRIVER),
    EASY_PICKUP("Easy to find", Sentiment.POSITIVE, AccountRole.DRIVER),
    CLEAR_DIRECTIONS("Clear directions", Sentiment.POSITIVE, AccountRole.DRIVER);

    /** Four stars and up is a good trip; anything less is somebody telling you something. */
    private static final int POSITIVE_FROM_STARS = 4;

    public enum Sentiment {
        POSITIVE,
        NEGATIVE
    }

    private final String label;
    private final Sentiment sentiment;
    private final AccountRole raterRole;

    RatingTag(String label, Sentiment sentiment, AccountRole raterRole) {
        this.label = label;
        this.sentiment = sentiment;
        this.raterRole = raterRole;
    }

    /** What the person tapping it reads. Also what the console shows an operator. */
    public String label() {
        return label;
    }

    public Sentiment sentiment() {
        return sentiment;
    }

    /** Which side of the trip may choose this - CUSTOMER tags are about a partner, and the reverse. */
    public AccountRole raterRole() {
        return raterRole;
    }

    public static Sentiment sentimentFor(int stars) {
        return stars >= POSITIVE_FROM_STARS ? Sentiment.POSITIVE : Sentiment.NEGATIVE;
    }

    /** The tags to offer somebody on this side of the trip who has just chosen this many stars. */
    public static List<RatingTag> offeredTo(AccountRole raterRole, int stars) {
        Sentiment wanted = sentimentFor(stars);
        return Arrays.stream(values())
                .filter(tag -> tag.raterRole == raterRole && tag.sentiment == wanted)
                .toList();
    }

    /**
     * Whether this tag belongs with this rating at all.
     * <p>
     * Checked on the way in rather than trusted from the app: a tag chosen at
     * four stars and then dragged down to one by a modified client would read
     * in the console as somebody's complaint, and nobody could tell.
     */
    public boolean allowedWith(AccountRole raterRole, int stars) {
        return this.raterRole == raterRole && this.sentiment == sentimentFor(stars);
    }
}
