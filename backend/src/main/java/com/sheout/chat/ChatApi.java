package com.sheout.chat;

import java.util.List;
import java.util.UUID;

/**
 * Chat's public interface. One method, for one caller: the ops console needs
 * to read a thread when settling a dispute, and it is the only thing outside
 * this module that has any business doing so.
 * <p>
 * Deliberately no send method here. Sending is self-service over HTTP by one
 * of the two people on the trip; nothing inside the backend should be able
 * to put words in either of their mouths.
 */
public interface ChatApi {

    /**
     * A whole thread, oldest first, with no participant check - the caller
     * is an admin acting under the console's own role gate. Empty for a
     * booking nobody wrote on.
     */
    List<ChatMessage> findThreadForAdmin(UUID bookingId);
}
