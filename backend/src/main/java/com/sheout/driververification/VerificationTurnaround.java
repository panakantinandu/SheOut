package com.sheout.driververification;

/**
 * How long a review actually takes, for the screen that has to tell somebody
 * how long she is waiting.
 * <p>
 * THE BASIS IS PART OF THE ANSWER. "Usually about two hours" and "we aim to
 * review within twelve hours" are different promises, and an app that says
 * the first while meaning the second is lying to a woman who is waiting to
 * get somewhere. So this carries whether the number came from real reviews
 * or from a target nobody has measured yet, and the screen says it either
 * way.
 *
 * @param typicalMinutes what to tell her, in minutes
 * @param measured       true when this came from reviews that really happened
 * @param sampleSize     how many reviews it was computed from, zero for a target
 */
public record VerificationTurnaround(int typicalMinutes, boolean measured, int sampleSize,
                                     /** When people review, "09:00" and "21:00" in India: the app says "between 9 am and 9 pm". */
                                     String reviewHoursFrom, String reviewHoursTo) {

    public VerificationTurnaround(int typicalMinutes, boolean measured, int sampleSize) {
        this(typicalMinutes, measured, sampleSize, null, null);
    }
}
