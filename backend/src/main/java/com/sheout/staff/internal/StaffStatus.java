package com.sheout.staff.internal;

/** Disabled is reversible - but only by an OWNER, so offboarding cannot be quietly undone by whoever did it. */
enum StaffStatus {
    ACTIVE,
    DISABLED
}
