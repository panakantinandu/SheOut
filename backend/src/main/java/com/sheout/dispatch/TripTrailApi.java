package com.sheout.dispatch;

import java.util.List;
import java.util.UUID;

/**
 * The positions a partner reported while a trip was under way - the path
 * actually driven, as far as her phone told us.
 * <p>
 * Recorded from the location reports the partner app already sends every
 * few seconds, from the moment the trip starts; nothing extra is asked of
 * the phone. Booking reads it once, at completion, to compare the path
 * driven with the route the fare was quoted on.
 */
public interface TripTrailApi {

    /** Oldest first. Empty when nothing was recorded (or it has expired, a day after the trip). */
    List<DriverLocation> trail(UUID bookingId);
}
