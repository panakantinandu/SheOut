package com.sheout.driververification.internal;

/**
 * The two moments before a submission that nothing recorded.
 * <p>
 * Deliberately two, and deliberately not "analytics". The question is
 * whether identity verification is where new riders give up, and it is
 * answered by comparing three numbers: who reached the screen, who got as
 * far as choosing a file, and who actually submitted - which the
 * verification record already knows. Anything more would be a general
 * event pipeline nobody asked for, collecting things nobody will read.
 */
public enum VerificationFunnelStep {

    /** She opened Identity Verification with nothing submitted yet. */
    UPLOAD_VIEWED,

    /** She picked a document, whether or not she went on to send it. */
    DOCUMENT_CHOSEN;

    /** The step of that name, or null - an unknown name is ignored, never an error. */
    public static VerificationFunnelStep parse(String name) {
        for (VerificationFunnelStep step : values()) {
            if (step.name().equalsIgnoreCase(name)) {
                return step;
            }
        }
        return null;
    }
}
