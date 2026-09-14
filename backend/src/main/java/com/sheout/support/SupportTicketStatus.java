package com.sheout.support;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a ticket is in its life.
 * <p>
 * The allowed moves are written here, next to the states, so there is one
 * place that answers "can this ticket go from A to B":
 * <ul>
 *   <li>OPEN - raised, nobody has picked it up.</li>
 *   <li>IN_PROGRESS - an operator is working on it.</li>
 *   <li>RESOLVED - an operator believes it is dealt with. It can be reopened,
 *       by an operator or by the raiser replying, because "resolved" is a
 *       claim the person who raised it may disagree with.</li>
 *   <li>CLOSED - finished. Final: nothing moves out of it and the raiser can
 *       no longer write to it.</li>
 * </ul>
 */
public enum SupportTicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public boolean canMoveTo(SupportTicketStatus next) {
        return switch (this) {
            case OPEN -> next == IN_PROGRESS || next == RESOLVED || next == CLOSED;
            case IN_PROGRESS -> next == OPEN || next == RESOLVED || next == CLOSED;
            case RESOLVED -> next == IN_PROGRESS || next == OPEN || next == CLOSED;
            case CLOSED -> false;
        };
    }

    /** OPEN and IN_PROGRESS: what the console's badge counts as still needing someone. */
    public static Set<SupportTicketStatus> unresolved() {
        return EnumSet.of(OPEN, IN_PROGRESS);
    }
}
