package com.sheout.notifications;

import java.util.UUID;

/**
 * A push to one member of staff's registered browsers: the staff module's
 * security alerts to owners. The text is plain and holds no personal data
 * beyond a staff member's name.
 */
public interface StaffAlertApi {

    void alertStaff(UUID staffAccountId, String title, String body);
}
