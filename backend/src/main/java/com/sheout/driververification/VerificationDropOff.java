package com.sheout.driververification;

/**
 * How many people got to the identity screen and stopped there.
 * <p>
 * The one question this instrumentation exists to answer: is verification
 * where new riders give up? Three numbers, over a window, for one role.
 *
 * @param reachedUploadScreen accounts that opened it with nothing submitted
 * @param choseDocument       accounts that got as far as picking a file
 * @param neverSubmitted      accounts that reached it and still have no document on file
 * @param windowDays          how far back this counts
 */
public record VerificationDropOff(long reachedUploadScreen, long choseDocument, long neverSubmitted, int windowDays) {

    /** Of everybody who arrived, the share who never sent anything - 0 when nobody arrived. */
    public double abandonmentRate() {
        return reachedUploadScreen == 0 ? 0 : (double) neverSubmitted / reachedUploadScreen;
    }
}
