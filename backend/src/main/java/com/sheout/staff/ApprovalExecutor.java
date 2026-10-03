package com.sheout.staff;

/**
 * Does an approved action. Each module that owns a kind of action provides
 * one, as a bean; the staff module calls it when the second person approves,
 * with that person signed in (StaffContext), so "who did it" in the module's
 * own records is the approver.
 */
public interface ApprovalExecutor {

    Approvals.Kind kind();

    /** Does it, from the request's payload. Never throws for an expected refusal - say so in the outcome. */
    Outcome execute(String payload);

    /**
     * @param message      a sentence for the approver and the record
     * @param oneTimeValue something to show the approver once and never store
     *                     (an invitation link when email is not set up)
     */
    record Outcome(boolean ok, String message, String oneTimeValue) {

        public static Outcome done(String message) {
            return new Outcome(true, message, null);
        }

        public static Outcome failed(String message) {
            return new Outcome(false, message, null);
        }
    }
}
