package com.sheout.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Staff as other modules need to show them: a name next to "assigned to" or
 * "decided by", and who a ticket can be handed to. Keyed by the account id
 * those modules already store. No credentials, no email addresses.
 */
public interface StaffDirectory {

    /** "Asha (Support agent)" for a member of staff, or empty for any other account. */
    Optional<String> label(UUID accountId);

    /** Active staff holding this permission, for an "assign to" list. */
    List<StaffMember> activeWith(Permission permission);

    record StaffMember(UUID accountId, String displayName, StaffRole role) {
    }
}
