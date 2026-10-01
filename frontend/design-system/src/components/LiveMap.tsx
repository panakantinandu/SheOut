import {
  APILoadingStatus,
  APIProvider,
  Map as GoogleMap,
  useApiLoadingStatus,
  useMap,
} from '@vis.gl/react-google-maps';
import { LocateFixed, MapPinOff } from 'lucide-react';
import { ServiceArt } from './ServiceArt';
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import * as Sentry from '@sentry/react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';
import { SHEOUT_MAP_STYLE, SHEOUT_MAP_STYLE_DARK } from '../lib/mapStyle';
import { MarkerMotion, ON_ROUTE_METRES, lineBearingAt, metresBetween, prepareLine, projectOnLine, splitLine, type MotionFrame, type PreparedLine } from '../lib/routeMotion';
import { useTheme } from '../lib/theme';
import { tokens } from '../tokens';

const { colors } = tokens;

export interface MapMarker {
  key: string;
  lat: number;
  lng: number;
  label: string;
  /**
   * Visual role, not free-form styling - keeps the marker meanings consistent
   * across both apps. 'nearby' is the riders' pre-booking "partners near you":
   * small, unlabelled, at deliberately approximate positions.
   */
  kind: 'pickup' | 'drop' | 'driver' | 'nearby';
  /**
   * Direction of travel in degrees clockwise from north, for a 'driver'
   * marker - from the device's own compass heading when it has one. Leave it
   * out and the map works it out from how the marker has moved; with neither,
   * the bike badge simply shows no direction.
   */
  heading?: number | null;
}

/** One point on a drawn route. Deliberately the same shape the backend serves. */
export interface RoutePoint {
  lat: number;
  lng: number;
}

export interface LiveMapProps {
  markers: MapMarker[];
  /**
   * The road to draw, as the router gave it back.
   * <p>
   * Omit it, or pass an empty array, and no line is drawn at all. There is
   * deliberately no "join the markers with a straight line" fallback: a
   * straight line between two points in Hyderabad routinely crosses a lake,
   * and a partner reading it as a road is worse off than one who can see
   * there is no route to show.
   */
  route?: RoutePoint[];
  className?: string;
  /** Re-fit the viewport to the markers whenever they move. Paused once the user pans, until they tap recentre. */
  autoFit?: boolean;
  /**
   * Turns the map into a place picker: tapping anywhere reports that point,
   * and markers become draggable and report where they are dropped. Leave
   * it unset and the map stays a read-only view, which is what tracking and
   * the dashboard want.
   */
  onPick?: (lat: number, lng: number) => void;
  /** Where to open when there are no markers to fit. Defaults to Hyderabad. */
  center?: { lat: number; lng: number };
  /** Zoom for `center`. Ignored once markers exist and autoFit is on. */
  zoom?: number;
  /** Bump to move the view to `center` again even when the coordinates have not changed ("locate me" twice). */
  centerNonce?: number;
  /**
   * Fill the parent instead of being a 16rem card - for a screen whose main
   * surface is the map. The parent sets the size; Google's logo and Terms
   * stay along this element's own bottom edge, so whatever sits below the
   * map must sit below this element, never over it.
   */
  fill?: boolean;
  /** Space kept clear when fitting the view, in pixels - e.g. under chips floating over the top of the map. */
  fitPadding?: { top: number; right: number; bottom: number; left: number };
  /**
   * Navigation camera: keep the view centred on this point (her own
   * position) at street zoom, moving with every fix, instead of fitting the
   * markers. Panning by hand pauses it; the recentre button resumes it.
   * The map stays north-up - turning it with her heading needs a vector map
   * (VITE_GOOGLE_MAPS_MAP_ID) - and her marker's pointer shows her direction.
   */
  follow?: RoutePoint | null;
  /**
   * False for a glance-only map (the partner Home card): no pan, no zoom, no
   * recentre button. The page scrolls through it instead of the map eating
   * the gesture. Defaults to true.
   */
  interactive?: boolean;
  /** Called once each time Google creates a map for this component - what Google bills as a map load. */
  onMapLoad?: () => void;
  /**
   * Centre-pin picking, the way ride apps set a pickup: a pin fixed at the
   * middle of the map, and she moves the map under it. The pin lifts while
   * the map moves; when it settles, onIdle reports the point under the tip.
   * Use with autoFit off and `center` as where to start.
   */
  centerPin?: CenterPinOptions;
}

export interface CenterPinOptions {
  kind: 'pickup' | 'drop';
  /** A short label over the pin while it rests ("Pickup here"). */
  label?: string;
  onMoveStart?: () => void;
  onIdle: (lat: number, lng: number) => void;
}

const API_KEY: string = import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? '';
// Optional: a Cloud Console map style. When set it replaces SHEOUT_MAP_STYLE.
const MAP_ID: string | undefined = import.meta.env.VITE_GOOGLE_MAPS_MAP_ID || undefined;

/**
 * Google reports a refused key - wrong site, API not enabled, billing off -
 * by calling a global gm_authFailure(), and only then paints its own grey
 * error box. The wrapper library has a status for this but never sets it, so
 * it is caught here, once for the page: every map switches to the plain
 * "unavailable" panel, and the failure is reported, because a refused key in
 * production is a configuration fault somebody needs to hear about.
 */
let mapsAuthFailed = false;
const authListeners = new Set<() => void>();
if (typeof window !== 'undefined') {
  const w = window as unknown as { gm_authFailure?: () => void };
  const previous = w.gm_authFailure;
  w.gm_authFailure = () => {
    if (!mapsAuthFailed) Sentry.captureMessage('Google Maps refused this key for this site (gm_authFailure)', 'error');
    mapsAuthFailed = true;
    authListeners.forEach((notify) => notify());
    previous?.();
  };
}
function useMapsAuthFailed(): boolean {
  return useSyncExternalStore(
    (notify) => {
      authListeners.add(notify);
      return () => authListeners.delete(notify);
    },
    () => mapsAuthFailed
  );
}

const HYDERABAD = { lat: 17.385, lng: 78.4867 };
/** Movement below this is GPS jitter, not a direction of travel. */
const MIN_MOVE_FOR_BEARING_METRES = 8;

/**
 * The one map both apps use: Google Maps, in SheOut's own quiet style.
 * <p>
 * WHAT CHANGED FROM LEAFLET, AND WHAT DID NOT. The interface is the same one
 * every screen already calls - markers, route, autoFit, onPick, center, zoom
 * - so no screen changed to move over. What went: OpenStreetMap's tiles, the
 * attribution bar and the +/- zoom buttons. Pinch and scroll still zoom. What
 * stays, because Google's terms require it: the small Google logo and the
 * "Terms" link along the bottom edge.
 * <p>
 * MARKERS ARE THE APP'S OWN, NOT GOOGLE PINS. Each is a small piece of React
 * placed on the map through an OverlayView, so they can be the brand's pins
 * and bike badges, rotate with a partner's heading, and be dragged in the
 * place picker - all without Google's Advanced Markers, which require a Map ID
 * and would rule out the JSON style below.
 * <p>
 * The partner's marker glides between fixes instead of jumping (see
 * routeMotion): along the road when she is on the route, so it only passes
 * through places she has really been, and never runs ahead of her newest
 * fix. The route is drawn in two colours cut at her position - grey behind
 * her, brand purple still to go - the way ride-hailing maps show progress.
 * <p>
 * If the map cannot load - no key in this build, the key refused for this
 * site, the network down - the space shows a plain "map unavailable" panel
 * rather than Google's grey error box. Every screen that has a map still
 * says everything important in text beside it.
 */
export function LiveMap(props: LiveMapProps) {
  const heightClass = cn(props.fill ? 'h-full w-full overflow-hidden' : 'h-64 w-full overflow-hidden rounded-card', props.className);
  if (!API_KEY) return <MapUnavailable className={heightClass} />;
  return (
    <APIProvider apiKey={API_KEY}>
      <LoadedMap {...props} className={heightClass} />
    </APIProvider>
  );
}

function LoadedMap({ markers, route, className, autoFit = true, onPick, center, zoom, fitPadding, follow, interactive = true, onMapLoad, centerPin, centerNonce }: LiveMapProps & { className: string }) {
  const [pinMoving, setPinMoving] = useState(false);
  const status = useApiLoadingStatus();
  const authFailed = useMapsAuthFailed();
  const [, , theme] = useTheme();
  const [initialCenter] = useState(() => center ?? (markers[0] ? { lat: markers[0].lat, lng: markers[0].lng } : HYDERABAD));
  const [initialZoom] = useState(() => zoom ?? (center || markers[0] ? 14 : 11));

  // Callers rebuild the route array on some renders; the line is prepared
  // again only when the road itself is different.
  const routeKey = route && route.length >= 2
    ? `${route.length}:${route[0].lat},${route[0].lng}:${route[route.length >> 1].lat},${route[route.length >> 1].lng}:${route[route.length - 1].lat},${route[route.length - 1].lng}`
    : '';
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const line = useMemo(() => prepareLine(route), [routeKey]);

  // The partner: her marker glides, and the route is cut where she is.
  const driver = markers.find((m) => m.kind === 'driver');
  const [motion] = useState(() => new MarkerMotion());
  useEffect(() => () => motion.dispose(), [motion]);
  // Line first, then the fix: a fix is placed against the route it belongs to.
  useEffect(() => motion.setLine(line), [motion, line]);
  useEffect(() => {
    motion.setTarget(driver ? { lat: driver.lat, lng: driver.lng } : null);
  }, [motion, driver?.lat, driver?.lng]);
  // On the route, her pointer follows the road ahead rather than the wobble
  // between two fixes.
  const roadHeading = useMemo(() => {
    if (!line || !driver) return null;
    const proj = projectOnLine(line, driver);
    return proj.dist <= ON_ROUTE_METRES && proj.along < line.total - 5 ? lineBearingAt(line, proj.along) : null;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [line, driver?.lat, driver?.lng]);

  if (authFailed || status === APILoadingStatus.AUTH_FAILURE || status === APILoadingStatus.FAILED) {
    return <MapUnavailable className={className} />;
  }

  return (
    <div className={cn('relative', className)} data-testid="live-map">
      <GoogleMap
        // colorScheme is fixed when a map is created, and maps are reused
        // between screens (reuseMaps), so each theme gets its own instance.
        key={theme === 'dark' ? 'dark' : 'light'}
        // Google's own interface in the matching scheme: the "Map data /
        // Terms" strip and the logo come in their dark variant on a dark
        // map, instead of a light box cutting across it. This is Google
        // restyling its own attribution; nothing here overrides it.
        colorScheme={theme === 'dark' ? 'DARK' : 'LIGHT'}
        defaultCenter={initialCenter}
        defaultZoom={initialZoom}
        mapId={MAP_ID}
        // The map follows the app: a white rectangle in a dark app at 11pm
        // is the brightest thing on the screen and the one thing she is
        // looking at for the longest.
        styles={MAP_ID ? undefined : theme === 'dark' ? SHEOUT_MAP_STYLE_DARK : SHEOUT_MAP_STYLE}
        disableDefaultUI
        scaleControl={false}
        clickableIcons={false}
        keyboardShortcuts={false}
        gestureHandling={interactive ? 'greedy' : 'none'}
        // Map loads are what Google bills for. Reusing the instance between
        // screens makes going back and forth one load, not one per visit.
        reuseMaps
        onClick={onPick ? (e) => e.detail.latLng && onPick(e.detail.latLng.lat, e.detail.latLng.lng) : undefined}
        className="h-full w-full"
      >
        {line && <RouteLines line={line} motion={driver ? motion : null} dark={theme === 'dark'} />}
        <Markers markers={markers} draggable={Boolean(onPick)} onPick={onPick} motion={motion} motionKey={driver?.key} roadHeading={roadHeading} />
        {onMapLoad && <MapLoadReporter onLoad={onMapLoad} />}
        {follow ? (
          <FollowCamera follow={follow} motion={driver ? motion : null} />
        ) : (
          <CameraControl markers={markers} route={route} autoFit={autoFit} center={center} zoom={zoom} centerNonce={centerNonce} fitPadding={fitPadding} showRecentre={interactive} />
        )}
        {theme === 'dark' && <AttributionScrim />}
        {centerPin && <CenterPinReporter options={centerPin} setMoving={setPinMoving} />}
      </GoogleMap>
      {centerPin && <CenterPin kind={centerPin.kind} label={centerPin.label} moving={pinMoving} />}
    </div>
  );
}

/** The road already covered: present, but stepping back behind the part still to go. */
const ROUTE_DONE = { light: '#A9A3BA', dark: '#5A536F' };

/**
 * The route, cut where the partner is: grey behind her, purple ahead, on a
 * white casing that keeps it crisp where it crosses roads and labels.
 * <p>
 * Drawn with Google's Polyline directly rather than through React, because
 * the cut moves on every animation frame of her glide and a React render
 * per frame would be wasted work. Where she is not on the route (not yet on
 * it, or detoured off it), the cut stays at the furthest point she reached;
 * with no partner on the map at all, the whole line is still to go.
 */
function RouteLines({ line, motion, dark }: { line: PreparedLine; motion: MarkerMotion | null; dark: boolean }) {
  const map = useMap();
  useEffect(() => {
    if (!map) return;
    // Under the markers: the line is context, the pin it ends at is the thing being looked for.
    const base = { clickable: false, map, strokeOpacity: 0.95 };
    const casing = new google.maps.Polyline({ ...base, path: line.points, strokeColor: '#ffffff', strokeWeight: 10, zIndex: 0 });
    const done = new google.maps.Polyline({ ...base, path: [], strokeColor: dark ? ROUTE_DONE.dark : ROUTE_DONE.light, strokeWeight: 6, zIndex: 1 });
    const ahead = new google.maps.Polyline({ ...base, path: line.points, strokeColor: colors.primary, strokeWeight: 6, zIndex: 2 });

    let cut = 0;
    const apply = (frame: MotionFrame | null) => {
      const along = frame?.along;
      // Half a metre is below a pixel at street zoom; no need to redraw for less.
      if (along == null || Math.abs(along - cut) < 0.5) return;
      cut = along;
      const parts = splitLine(line, cut);
      done.setPath(parts.done);
      ahead.setPath(parts.ahead);
    };
    const unsubscribe = motion?.subscribe(apply);
    apply(motion?.current() ?? null);
    return () => {
      unsubscribe?.();
      casing.setMap(null);
      done.setMap(null);
      ahead.setMap(null);
    };
  }, [map, line, motion, dark]);
  return null;
}

/**
 * Keeps the view on the markers that matter, the way the Leaflet map did -
 * but only while the user has not taken over. Pan or zoom by hand and the
 * map stays where she put it, with a small recentre button to hand it back.
 * <p>
 * Fitted on the markers' positions, not on the array: callers rebuild their
 * marker list on every render, and re-fitting on each of those would fight
 * every gesture. 'nearby' markers are left out of the fit - they come and go
 * every few seconds, and the view should hold on the pickup, not on them.
 */
/**
 * A soft dark band along the bottom of a dark map, so Google's logo and its
 * "Map data / Terms" text read clearly without a hard strip.
 * <p>
 * It is drawn in the map's own lowest overlay layer (mapPane): above the
 * tiles and underneath every one of Google's controls, the attribution
 * included. It never covers, hides or restyles the attribution - Google's
 * terms forbid all three - it only darkens the map behind it. Kept pinned
 * to the bottom edge of the view as the map pans and zooms.
 */
function AttributionScrim() {
  const map = useMap();
  useEffect(() => {
    if (!map) return;
    const band = document.createElement('div');
    band.setAttribute('data-testid', 'attribution-scrim');
    band.style.cssText = 'position:absolute;height:56px;pointer-events:none;'
      + 'background:linear-gradient(to bottom, rgba(12,10,22,0) 0%, rgba(12,10,22,0.55) 70%, rgba(12,10,22,0.7) 100%);';
    const overlay = new google.maps.OverlayView();
    const place = () => {
      const projection = overlay.getProjection();
      const container = map.getDiv();
      if (!projection || !container) return;
      // Where the view's top-left corner is in the pane's own coordinates.
      const corner = projection.fromContainerPixelToLatLng(new google.maps.Point(0, 0));
      const origin = corner && projection.fromLatLngToDivPixel(corner);
      if (!origin) return;
      band.style.left = `${origin.x}px`;
      band.style.top = `${origin.y + container.clientHeight - 56}px`;
      band.style.width = `${container.clientWidth}px`;
    };
    overlay.onAdd = () => overlay.getPanes()?.mapPane.appendChild(band);
    overlay.draw = place;
    overlay.onRemove = () => band.remove();
    overlay.setMap(map);
    const listeners = ['bounds_changed', 'drag', 'zoom_changed', 'resize'].map((event) => map.addListener(event, place));
    return () => {
      listeners.forEach((l) => l.remove());
      overlay.setMap(null);
    };
  }, [map]);
  return null;
}

function CameraControl({ markers, route, autoFit, center, zoom, centerNonce, fitPadding, showRecentre = true }: { markers: MapMarker[]; route?: RoutePoint[]; autoFit: boolean; center?: RoutePoint; zoom?: number; centerNonce?: number; fitPadding?: LiveMapProps['fitPadding']; showRecentre?: boolean }) {
  const map = useMap();
  const [userMoved, setUserMoved] = useState(false);
  const [fitNonce, setFitNonce] = useState(0);
  // Fitting handed back on (a pin chosen by moving the map, say): the moves
  // made while it was off were not her taking over the view, so the view
  // fits again - the new pin and the route both in sight.
  useEffect(() => {
    if (autoFit) setUserMoved(false);
  }, [autoFit]);
  const fitPoints = markers.filter((m) => m.kind !== 'nearby');
  // The partner moves with every fix. Re-fitting the view each time is what
  // made the map pump in and out every few seconds, so her position is kept
  // out of the signature: the view is fitted on the places that stay put and
  // follows her only when she needs it to (below).
  const fixedPoints = fitPoints.filter((m) => m.kind !== 'driver');
  const driver = fitPoints.find((m) => m.kind === 'driver');
  // The whole road, not only its ends: a route that bends round a lake runs
  // well outside the box its two pins make, and was drawn off the map.
  const routePoints = route && route.length >= 2 ? route : [];
  const signature = fixedPoints.map((m) => `${m.lat.toFixed(5)},${m.lng.toFixed(5)}`).join('|')
    + (routePoints.length ? `|route:${routePoints.length}:${routePoints[0].lat.toFixed(5)},${routePoints[routePoints.length - 1].lng.toFixed(5)}` : '')
    + (driver ? '|driver' : '');
  const latest = useRef({ fitPoints, routePoints, fitPadding });
  latest.current = { fitPoints, routePoints, fitPadding };

  useEffect(() => {
    if (!map) return;
    const listener = map.addListener('dragstart', () => setUserMoved(true));
    return () => listener.remove();
  }, [map]);

  // One fit, capped at street zoom in the same move: fitBounds takes no
  // maxZoom, and zooming back out after it landed was a visible second step.
  const fit = useCallback(() => {
    if (!map) return () => undefined;
    const { fitPoints: points, routePoints: road, fitPadding: padding } = latest.current;
    if (points.length === 0) return () => undefined;
    if (points.length === 1 && road.length === 0) {
      map.panTo(points[0]);
      if ((map.getZoom() ?? 0) < 14) map.setZoom(15);
      return () => undefined;
    }
    const bounds = new google.maps.LatLngBounds();
    points.forEach((p) => bounds.extend(p));
    road.forEach((p) => bounds.extend(p));
    map.setOptions({ maxZoom: 16 });
    map.fitBounds(bounds, padding ?? 48);
    const once = google.maps.event.addListenerOnce(map, 'idle', () => map.setOptions({ maxZoom: null }));
    return () => {
      once.remove();
      map.setOptions({ maxZoom: null });
    };
  }, [map]);

  useEffect(() => {
    if (!map || !autoFit || userMoved || fitPoints.length === 0) return;
    return fit();
    // signature stands in for fitPoints; see the comment above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, autoFit, userMoved, signature, fitNonce, fit]);

  // Her position moves the view only when it has to: when she is about to
  // leave it, or when she and the places around her have drawn so close
  // together that the view is mostly empty map (she is nearly at the
  // pickup). Otherwise the view holds still and her marker moves across it.
  useEffect(() => {
    if (!map || !autoFit || userMoved || !driver) return;
    const view = map.getBounds();
    if (!view) return;
    const ne = view.getNorthEast();
    const sw = view.getSouthWest();
    const latSpan = ne.lat() - sw.lat();
    const lngSpan = ne.lng() - sw.lng();
    const inner = (p: RoutePoint) =>
      p.lat < ne.lat() - latSpan * 0.15 && p.lat > sw.lat() + latSpan * 0.15
      && p.lng < ne.lng() - lngSpan * 0.12 && p.lng > sw.lng() + lngSpan * 0.12;
    const all = [...fitPoints, ...routePoints];
    const lats = all.map((p) => p.lat);
    const lngs = all.map((p) => p.lng);
    const needLat = Math.max(...lats) - Math.min(...lats);
    const needLng = Math.max(...lngs) - Math.min(...lngs);
    const muchTooWide = all.length > 1 && needLat < latSpan * 0.3 && needLng < lngSpan * 0.3 && (map.getZoom() ?? 0) < 16;
    if (!inner(driver) || muchTooWide) return fit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, autoFit, userMoved, driver?.lat, driver?.lng, fit]);

  // A picker's opening view. Keyed on the coordinates, so a caller
  // re-rendering with the same place does not yank the view mid-pan.
  useEffect(() => {
    if (!map || !center) return;
    map.setCenter(center);
    map.setZoom(zoom ?? 14);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, center?.lat, center?.lng, zoom, centerNonce]);

  if (!showRecentre || !autoFit || !userMoved || fitPoints.length === 0) return null;
  return (
    <RecentreButton
      onClick={() => {
        setUserMoved(false);
        setFitNonce((n) => n + 1);
      }}
    />
  );
}

/**
 * Map instances already reported. reuseMaps hands a screen the same Google
 * map again when it comes back, and Google bills a load only when a map is
 * created, so a returning map is not counted twice.
 */
const reportedMaps = new WeakSet<google.maps.Map>();

/** Reports each map instance Google creates (a billed map load), once per instance. */
function MapLoadReporter({ onLoad }: { onLoad: () => void }) {
  const map = useMap();
  useEffect(() => {
    if (map && !reportedMaps.has(map)) {
      reportedMaps.add(map);
      onLoad();
    }
  }, [map, onLoad]);
  return null;
}

/** Street zoom for navigation: the next turn and the road names around it readable at a glance. */
const FOLLOW_ZOOM = 17;

/**
 * The navigation camera: centred on her, at street zoom, moving with each
 * GPS fix. A drag hands the map to her (to look ahead, or back at a turn);
 * the recentre button hands it back.
 */
function FollowCamera({ follow, motion }: { follow: RoutePoint; motion: MarkerMotion | null }) {
  const map = useMap();
  const [userMoved, setUserMoved] = useState(false);
  const zoomed = useRef(false);

  useEffect(() => {
    if (!map) return;
    const listener = map.addListener('dragstart', () => setUserMoved(true));
    return () => listener.remove();
  }, [map]);

  // With her marker on the map, the camera rides with it frame by frame, so
  // the view slides along the road instead of lurching once a second with
  // each fix, and her marker stays still at the centre as navigation apps do.
  useEffect(() => {
    if (!map || userMoved || !motion) return;
    const centre = (frame: MotionFrame | null) => {
      if (!frame) return;
      if (!zoomed.current) {
        map.setZoom(FOLLOW_ZOOM);
        zoomed.current = true;
      }
      map.setCenter(frame.position);
    };
    centre(motion.current());
    return motion.subscribe(centre);
  }, [map, userMoved, motion]);

  useEffect(() => {
    if (!map || userMoved || (motion && motion.current())) return;
    if (!zoomed.current) {
      map.setZoom(FOLLOW_ZOOM);
      map.setCenter(follow);
      zoomed.current = true;
    } else {
      map.panTo(follow);
    }
  }, [map, userMoved, motion, follow.lat, follow.lng]);

  if (!userMoved) return null;
  return (
    <RecentreButton
      onClick={() => {
        zoomed.current = false;
        setUserMoved(false);
      }}
    />
  );
}

function RecentreButton({ onClick }: { onClick: () => void }) {
  const { t } = useTranslation('ds');
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={t('map.recentre')}
      title={t('map.recentre')}
      className="absolute bottom-7 right-2 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-surface text-primary shadow-card"
      data-testid="map-recentre"
    >
      <LocateFixed className="h-5 w-5" />
    </button>
  );
}

function Markers({
  markers,
  draggable,
  onPick,
  motion,
  motionKey,
  roadHeading,
}: {
  markers: MapMarker[];
  draggable: boolean;
  onPick?: (lat: number, lng: number) => void;
  /** Moves the marker with this key between fixes. */
  motion: MarkerMotion;
  motionKey?: string;
  /** The road's direction where that marker is, when it is on the route. */
  roadHeading: number | null;
}) {
  // Last position and bearing per marker, to point a partner's badge the way
  // she is going when her device does not report a heading itself.
  const last = useRef(new Map<string, { lat: number; lng: number; bearing: number | null }>());

  const bearings = useMemo(() => {
    const out = new Map<string, number | null>();
    for (const m of markers) {
      if (m.kind !== 'driver') continue;
      const prev = last.current.get(m.key);
      let bearing = prev?.bearing ?? null;
      if (prev && metresBetween(prev, m) >= MIN_MOVE_FOR_BEARING_METRES) bearing = bearingBetween(prev, m);
      if (!prev || metresBetween(prev, m) >= MIN_MOVE_FOR_BEARING_METRES) last.current.set(m.key, { lat: m.lat, lng: m.lng, bearing });
      // Her own compass first, then the road she is on, then how she has moved.
      const road = m.key === motionKey ? roadHeading : null;
      out.set(m.key, typeof m.heading === 'number' && Number.isFinite(m.heading) ? m.heading : road ?? bearing);
    }
    return out;
  }, [markers, motionKey, roadHeading]);

  return (
    <>
      {markers.map((m) => (
        <HtmlMarker
          key={m.key}
          position={m}
          anchor={m.kind === 'pickup' || m.kind === 'drop' ? 'bottom' : 'center'}
          zIndex={m.kind === 'nearby' ? 1 : m.kind === 'driver' ? 3 : 2}
          draggable={draggable && m.kind !== 'nearby'}
          onDragEnd={onPick}
          title={m.kind === 'nearby' ? undefined : m.label}
          motion={m.key === motionKey && !draggable ? motion : undefined}
        >
          <MarkerGlyph kind={m.kind} heading={bearings.get(m.key) ?? null} />
        </HtmlMarker>
      ))}
    </>
  );
}

function MarkerGlyph({ kind, heading }: { kind: MapMarker['kind']; heading: number | null }) {
  const turned = useRef<number | null>(null);
  if (kind === 'pickup' || kind === 'drop') {
    const fill = kind === 'pickup' ? colors.primary : colors.brandOrange;
    return (
      <svg
        width="30"
        height="38"
        viewBox="0 0 30 38"
        aria-hidden="true"
        style={{ display: 'block', filter: 'drop-shadow(0 2px 3px rgba(36,26,51,.35))' }}
        data-testid={`${kind}-marker`}
      >
        <path d="M15 1C7.3 1 1 7.1 1 14.7 1 25 15 37 15 37s14-12 14-22.3C29 7.1 22.7 1 15 1z" fill={fill} stroke="#fff" strokeWidth="2" />
        <circle cx="15" cy="14.5" r="5" fill="#fff" />
      </svg>
    );
  }
  if (kind === 'nearby') {
    return (
      <span
        className="flex h-7 w-7 items-center justify-center rounded-full border border-border bg-surface text-primary"
        style={{ boxShadow: '0 1px 4px rgba(36,26,51,.25)' }}
        data-testid="nearby-driver"
        aria-hidden="true"
      >
        <ServiceArt kind="ride" size="xs" className="rounded-md" />
      </span>
    );
  }
  // The partner: a bike badge, with a small pointer on its rim that turns to
  // face her direction of travel. The bike itself stays upright - a side-on
  // bike rotated to face south would be drawn upside down.
  // Turned the short way round: 350 to 10 degrees is a 20-degree turn, not a
  // spin almost all the way back through south.
  if (heading != null) {
    const prev = turned.current;
    turned.current = prev == null ? heading : prev + ((((heading - prev) % 360) + 540) % 360) - 180;
  }
  return (
    <span className="relative flex h-10 w-10 items-center justify-center" data-testid="driver-marker" data-heading={heading ?? ''}>
      {heading != null && (
        <span className="absolute inset-0" style={{ transform: `rotate(${turned.current}deg)`, transition: 'transform 600ms ease-out' }} aria-hidden="true">
          <span
            className="absolute left-1/2 top-[-7px] h-0 w-0 -translate-x-1/2"
            style={{ borderLeft: '6px solid transparent', borderRight: '6px solid transparent', borderBottom: `9px solid ${colors.primaryDark}` }}
          />
        </span>
      )}
      <span
        className="flex h-10 w-10 items-center justify-center overflow-hidden rounded-full border-[3px] border-white text-white"
        style={{ background: colors.primary, boxShadow: '0 2px 6px rgba(36,26,51,.4)' }}
      >
        <ServiceArt kind="ride" className="h-9 w-9 scale-125" />
      </span>
    </span>
  );
}

/**
 * A React element pinned to a map position, through Google's OverlayView.
 * Drag support is for the place picker: pointer events on the marker itself,
 * with the map's own panning held off while it is being dragged.
 */
function HtmlMarker({
  position,
  anchor,
  zIndex,
  draggable,
  onDragEnd,
  title,
  motion,
  children,
}: {
  position: RoutePoint;
  anchor: 'center' | 'bottom';
  zIndex: number;
  draggable: boolean;
  onDragEnd?: (lat: number, lng: number) => void;
  title?: string;
  /** Where it is drawn comes from here, frame by frame, instead of straight from `position`. */
  motion?: MarkerMotion;
  children: ReactNode;
}) {
  const map = useMap();
  const [container] = useState(() => document.createElement('div'));
  const overlayRef = useRef<google.maps.OverlayView | null>(null);
  const positionRef = useRef<RoutePoint>(position);
  const dragRef = useRef<RoutePoint | null>(null);
  positionRef.current = motion?.current()?.position ?? position;

  useEffect(() => {
    if (!motion) return;
    return motion.subscribe((frame) => {
      if (!frame) return;
      positionRef.current = frame.position;
      overlayRef.current?.draw();
    });
  }, [motion]);

  useEffect(() => {
    if (!map) return;
    container.style.position = 'absolute';
    const overlay = new google.maps.OverlayView();
    overlay.onAdd = () => overlay.getPanes()?.overlayMouseTarget.appendChild(container);
    overlay.draw = () => {
      const at = overlay.getProjection()?.fromLatLngToDivPixel(dragRef.current ?? positionRef.current);
      if (!at) return;
      container.style.left = `${at.x}px`;
      container.style.top = `${at.y}px`;
    };
    overlay.onRemove = () => container.remove();
    overlay.setMap(map);
    overlayRef.current = overlay;
    return () => {
      overlay.setMap(null);
      overlayRef.current = null;
    };
  }, [map, container]);

  // Re-place on every position the caller reports.
  useEffect(() => {
    overlayRef.current?.draw();
  }, [position.lat, position.lng]);

  useEffect(() => {
    container.style.transform = anchor === 'bottom' ? 'translate(-50%, -100%)' : 'translate(-50%, -50%)';
    container.style.zIndex = String(zIndex);
    container.style.cursor = draggable ? 'grab' : '';
    container.style.touchAction = draggable ? 'none' : '';
    if (title) container.title = title;
    else container.removeAttribute('title');
  }, [container, anchor, zIndex, draggable, title]);

  useEffect(() => {
    if (!map || !draggable) return;
    google.maps.OverlayView.preventMapHitsAndGesturesFrom(container);
    let pointerId: number | null = null;
    const toLatLng = (e: PointerEvent): RoutePoint | null => {
      const rect = map.getDiv().getBoundingClientRect();
      const ll = overlayRef.current?.getProjection()?.fromContainerPixelToLatLng(
        new google.maps.Point(e.clientX - rect.left, e.clientY - rect.top)
      );
      return ll ? { lat: ll.lat(), lng: ll.lng() } : null;
    };
    const down = (e: PointerEvent) => {
      pointerId = e.pointerId;
      container.setPointerCapture(e.pointerId);
      container.style.cursor = 'grabbing';
    };
    const move = (e: PointerEvent) => {
      if (pointerId !== e.pointerId) return;
      dragRef.current = toLatLng(e);
      overlayRef.current?.draw();
    };
    const up = (e: PointerEvent) => {
      if (pointerId !== e.pointerId) return;
      pointerId = null;
      container.style.cursor = 'grab';
      const dropped = dragRef.current;
      dragRef.current = null;
      if (dropped) onDragEnd?.(dropped.lat, dropped.lng);
      overlayRef.current?.draw();
    };
    container.addEventListener('pointerdown', down);
    container.addEventListener('pointermove', move);
    container.addEventListener('pointerup', up);
    container.addEventListener('pointercancel', up);
    return () => {
      container.removeEventListener('pointerdown', down);
      container.removeEventListener('pointermove', move);
      container.removeEventListener('pointerup', up);
      container.removeEventListener('pointercancel', up);
    };
  }, [map, container, draggable, onDragEnd]);

  return createPortal(children, container);
}

/**
 * Reports the point under a centre pin: that the map started moving, and
 * where its centre is once it settles. Google fires 'idle' after every pan,
 * zoom and programmatic move, so one listener covers a drag, a pinch and the
 * caller re-centring on her location.
 */
function CenterPinReporter({ options, setMoving }: { options: CenterPinOptions; setMoving: (moving: boolean) => void }) {
  const map = useMap();
  const latest = useRef(options);
  latest.current = options;
  useEffect(() => {
    if (!map) return;
    let settled = false;
    const report = () => {
      const c = map.getCenter();
      if (c) latest.current.onIdle(c.lat(), c.lng());
    };
    const start = () => {
      setMoving(true);
      latest.current.onMoveStart?.();
    };
    const listeners = [
      map.addListener('dragstart', start),
      map.addListener('zoom_changed', start),
      map.addListener('idle', () => {
        settled = true;
        setMoving(false);
        report();
      }),
    ];
    // A reused map that is already still fires no idle of its own; report
    // where it rests unless a move the caller just asked for gets there first.
    const fallback = window.setTimeout(() => {
      if (!settled) report();
    }, 400);
    return () => {
      window.clearTimeout(fallback);
      listeners.forEach((l) => l.remove());
      setMoving(false);
    };
  }, [map, setMoving]);
  return null;
}

/**
 * The pin itself: drawn over the map, not on it, so it stays at the centre
 * while the map moves under it. Its tip is exactly the map's centre. It
 * lifts and its shadow shrinks while the map is moving, and drops back when
 * the map settles - the cue that the address below is about to update.
 */
function CenterPin({ kind, label, moving }: { kind: 'pickup' | 'drop'; label?: string; moving: boolean }) {
  const fill = kind === 'pickup' ? colors.primary : colors.brandOrange;
  return (
    <div className="pointer-events-none absolute inset-0 z-[5]" aria-hidden="true" data-testid="center-pin" data-moving={moving}>
      {/* The spot on the ground the tip points at. */}
      <span
        className="absolute left-1/2 top-1/2 h-2 w-4 rounded-[50%] bg-black/30 transition-transform duration-150"
        style={{ transform: `translate(-50%, -50%) scale(${moving ? 0.6 : 1})` }}
      />
      <div
        className="absolute left-1/2 top-1/2 flex flex-col items-center transition-transform duration-150 ease-out"
        style={{ transform: `translate(-50%, -100%) translateY(${moving ? -14 : 0}px)` }}
      >
        {label && (
          <span
            className={cn(
              'mb-1.5 whitespace-nowrap rounded-full px-3 py-1 text-xs font-semibold text-white shadow-float transition-opacity duration-150',
              moving ? 'opacity-0' : 'opacity-100'
            )}
            style={{ background: fill }}
          >
            {label}
          </span>
        )}
        <svg width="34" height="44" viewBox="0 0 30 38" style={{ display: 'block', filter: 'drop-shadow(0 3px 4px rgba(36,26,51,.35))' }}>
          <path d="M15 1C7.3 1 1 7.1 1 14.7 1 25 15 37 15 37s14-12 14-22.3C29 7.1 22.7 1 15 1z" fill={fill} stroke="#fff" strokeWidth="2" />
          <circle cx="15" cy="14.5" r="5" fill="#fff" />
        </svg>
      </div>
    </div>
  );
}

function MapUnavailable({ className }: { className: string }) {
  const { t } = useTranslation('ds');
  return (
    <div
      className={cn('flex flex-col items-center justify-center gap-2 bg-primary-light text-center', className)}
      data-testid="map-unavailable"
    >
      <MapPinOff className="h-6 w-6 text-primary" aria-hidden="true" />
      <p className="px-6 text-sm text-text-secondary">{t('map.unavailable')}</p>
    </div>
  );
}

/** Initial bearing from a to b, degrees clockwise from north. */
function bearingBetween(a: RoutePoint, b: RoutePoint): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const y = Math.sin(toRad(b.lng - a.lng)) * Math.cos(toRad(b.lat));
  const x = Math.cos(toRad(a.lat)) * Math.sin(toRad(b.lat))
    - Math.sin(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.cos(toRad(b.lng - a.lng));
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
}
