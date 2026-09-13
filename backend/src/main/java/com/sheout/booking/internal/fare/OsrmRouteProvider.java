package com.sheout.booking.internal.fare;

import com.sheout.booking.GeoAddress;
import com.sheout.sharedkernel.geo.GeoDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Real road distance and duration from OSRM.
 * <p>
 * OSRM is open source and built on the same OpenStreetMap data the maps and
 * address search in this product already use, so it needs no new vendor, no
 * API key and no account. That is why it was chosen over a commercial
 * routing API.
 * <p>
 * PUBLIC DEMO SERVER, AND THAT IS A DEBT, NOT A DECISION. router.project-osrm.org
 * is run by the OSRM project as a demonstration. It has documented rate
 * limits, no uptime guarantee, no support, and its usage policy does not
 * contemplate a commercial service running its pricing on it. It is fine
 * for development and for a launch at small volume. It is not fine as the
 * thing every fare in the city depends on.
 * <p>
 * The fix is to self-host: OSRM ships a Docker image, and a Hyderabad-sized
 * extract is a small amount of RAM. That is the right move once real
 * traffic justifies the instance, and not before - standing up
 * infrastructure for traffic that does not exist yet is its own kind of
 * waste. Point OSRM_BASE_URL at the self-hosted instance and nothing else
 * changes.
 * <p>
 * Uses JdkClientHttpRequestFactory rather than the default. The default is
 * backed by HttpURLConnection, which this codebase has already been bitten
 * by once: it consumed an error response body during authentication
 * handling and left a failure with no explanation at all.
 */
@Component
public class OsrmRouteProvider implements RouteProvider {

    private static final Logger log = LoggerFactory.getLogger(OsrmRouteProvider.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final double roadDistanceFactor;
    private final double fallbackSpeedKmph;

    OsrmRouteProvider(
            @Value("${sheout.routing.osrm-base-url:https://router.project-osrm.org}") String baseUrl,
            @Value("${sheout.routing.timeout-ms:4000}") long timeoutMs,
            // How much longer a real road route is than the straight line,
            // used only when routing is unavailable. 1.4 is a common figure
            // for dense Indian city grids; it is a guess to be checked
            // against real routed distances once there are enough of them.
            @Value("${sheout.routing.road-distance-factor:1.4}") double roadDistanceFactor,
            // Hyderabad traffic, not open road. Used only for the fallback
            // duration.
            @Value("${sheout.routing.fallback-speed-kmph:22}") double fallbackSpeedKmph) {
        this.baseUrl = baseUrl;
        this.roadDistanceFactor = roadDistanceFactor;
        this.fallbackSpeedKmph = fallbackSpeedKmph;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                // Identifies this service to the OSRM operators, the same
                // courtesy the Nominatim client already observes. An
                // anonymous client hammering a volunteer-run endpoint is how
                // a whole project gets blocked.
                .defaultHeader("User-Agent", "SheOut/1.0 (+https://sheout-customer-app.vercel.app)")
                .build();
        log.info("Routing via {} (timeout {}ms)", baseUrl, timeoutMs);
    }

    /**
     * The route, or an honest estimate if the router cannot be reached.
     * <p>
     * Never throws. A rider must be able to book when a volunteer-run demo
     * server is rate-limiting us, and refusing the trip - or worse, pricing
     * it at zero - would be a far worse failure than pricing it from a
     * scaled straight line. The estimate is marked as such so nothing
     * downstream mistakes it for a measurement.
     */
    @Override
    public RouteEstimate route(GeoAddress pickup, GeoAddress drop) {
        try {
            // OSRM takes lng,lat - the opposite order to almost everything
            // else here. Getting this backwards produces a plausible-looking
            // route somewhere else entirely rather than an error.
            String path = "/route/v1/driving/%f,%f;%f,%f?overview=false"
                    .formatted(pickup.lng(), pickup.lat(), drop.lng(), drop.lat());

            OsrmResponse response = restClient.get()
                    .uri(baseUrl + path)
                    .retrieve()
                    .body(OsrmResponse.class);

            if (response == null || !"Ok".equals(response.code())
                    || response.routes() == null || response.routes().isEmpty()) {
                log.warn("OSRM returned no usable route ({}), falling back to an estimate",
                        response == null ? "no body" : response.code());
                return estimate(pickup, drop);
            }

            OsrmRoute route = response.routes().get(0);
            return new RouteEstimate(
                    route.distance() / 1000.0,
                    route.duration() / 60.0,
                    RouteEstimate.Source.ROUTED);
        } catch (RuntimeException e) {
            // Rate limiting, a timeout, DNS, a malformed body - all the same
            // to a rider standing on a pavement. Logged at warn because a
            // run of these is the signal that it is time to self-host.
            log.warn("OSRM routing failed ({}), falling back to an estimate", e.toString());
            return estimate(pickup, drop);
        }
    }

    /**
     * The route's shape, for a partner's navigation map.
     * <p>
     * Asks for GeoJSON geometry rather than OSRM's encoded-polyline default.
     * The encoded form is smaller, but decoding it means writing and
     * maintaining a decoder on both sides of the wire for a saving measured
     * in kilobytes, on a request made about twice per trip. GeoJSON is
     * coordinates, already parsed.
     * <p>
     * No fallback, unlike {@link #route}. A fare has to exist or a rider
     * cannot book; a drawn line does not, and inventing a straight one would
     * show a partner a road that is not there - through a lake, in
     * Hyderabad's case, more often than one would like.
     */
    @Override
    public RoutePath routePath(GeoAddress from, GeoAddress to) {
        try {
            // lng,lat - OSRM's order, not this codebase's. Reversing it
            // returns a confident route somewhere else entirely.
            String path = "/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson"
                    .formatted(from.lng(), from.lat(), to.lng(), to.lat());

            OsrmGeometryResponse response = restClient.get()
                    .uri(baseUrl + path)
                    .retrieve()
                    .body(OsrmGeometryResponse.class);

            if (response == null || !"Ok".equals(response.code())
                    || response.routes() == null || response.routes().isEmpty()) {
                log.warn("OSRM returned no drawable route ({})",
                        response == null ? "no body" : response.code());
                return RoutePath.unavailable();
            }

            OsrmGeometryRoute route = response.routes().get(0);
            if (route.geometry() == null || route.geometry().coordinates() == null) {
                return RoutePath.unavailable();
            }

            List<RoutePath.RoutePoint> points = route.geometry().coordinates().stream()
                    // Each entry is [lng, lat]. Flipped here, once, so
                    // nothing downstream has to remember.
                    .filter(pair -> pair.size() >= 2)
                    .map(pair -> new RoutePath.RoutePoint(pair.get(1), pair.get(0)))
                    .toList();

            return new RoutePath(points, route.distance() / 1000.0, route.duration() / 60.0);
        } catch (RuntimeException e) {
            log.warn("OSRM route geometry failed ({})", e.toString());
            return RoutePath.unavailable();
        }
    }

    /**
     * Straight-line distance scaled up, with duration from an assumed
     * average speed.
     * <p>
     * Deliberately not a straight haversine: roads are longer than lines,
     * and pricing an unrouted trip at the crow-flies distance would
     * systematically underpay every partner on every trip the router missed.
     * The factor errs towards the rider paying slightly more than the line
     * suggests, which is the error that does not come out of somebody's
     * earnings.
     */
    private RouteEstimate estimate(GeoAddress pickup, GeoAddress drop) {
        double straightLineKm = GeoDistance.haversineKm(pickup.lat(), pickup.lng(), drop.lat(), drop.lng());
        double roadKm = straightLineKm * roadDistanceFactor;
        double minutes = fallbackSpeedKmph <= 0 ? 0 : (roadKm / fallbackSpeedKmph) * 60.0;
        return new RouteEstimate(roadKm, minutes, RouteEstimate.Source.ESTIMATED);
    }

    /** Only the two fields pricing needs; OSRM returns a great deal more. */
    record OsrmResponse(String code, List<OsrmRoute> routes) {
    }

    /** distance is metres and duration is seconds, which is OSRM's contract, not a choice made here. */
    record OsrmRoute(double distance, double duration) {
    }

    /** The same response, plus the geometry the pricing call deliberately does not ask for. */
    record OsrmGeometryResponse(String code, List<OsrmGeometryRoute> routes) {
    }

    record OsrmGeometryRoute(double distance, double duration, OsrmGeometry geometry) {
    }

    /** GeoJSON LineString: a list of [lng, lat] pairs, in that order. */
    record OsrmGeometry(List<List<Double>> coordinates) {
    }
}
