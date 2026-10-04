package com.sheout.staff;

/**
 * Live pushes to open consoles (Server-Sent Events): a new SOS, a new trip
 * alert, an approval waiting. Each goes only to consoles whose signed-in
 * member of staff holds {@code audience}, and reaches every server instance
 * (through Redis), not just the one that raised it.
 * <p>
 * A push is a nudge, never the data: it says what happened and the record's
 * id; the console then fetches the record through the ordinary, permission-
 * checked endpoints. A console that misses a push (offline, Redis down) loses
 * nothing - it falls back to polling.
 */
public interface StaffLiveEvents {

    /** @param json a small JSON object: ids and kinds, nothing personal */
    void publish(String type, Permission audience, String json);
}
