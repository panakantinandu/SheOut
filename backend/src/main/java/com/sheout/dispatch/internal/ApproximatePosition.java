package com.sheout.dispatch.internal;

/**
 * A partner's position, blurred by 50-100 m, for the riders' "partners near
 * you" preview. Deliberately nothing else: no id, name, rating or vehicle - see
 * NearbyDriverPreview.
 */
public record ApproximatePosition(double lat, double lng) {
}
