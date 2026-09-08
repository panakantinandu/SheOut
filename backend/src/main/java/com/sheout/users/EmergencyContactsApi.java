package com.sheout.users;

import java.util.List;
import java.util.UUID;

/**
 * Split out from {@link CustomerProfileApi} on purpose: notifications
 * (not built yet) only ever needs this one narrow read, not the rest of a
 * customer's profile - depending on this interface instead of the full
 * profile API keeps that future dependency minimal and explicit.
 */
public interface EmergencyContactsApi {

    List<EmergencyContact> findContactsByAccountId(UUID customerAccountId);
}
