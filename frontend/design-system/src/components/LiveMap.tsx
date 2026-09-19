import {
  APILoadingStatus,
  APIProvider,
  Map as GoogleMap,
  Polyline,
  useApiLoadingStatus,
  useMap,
} from '@vis.gl/react-google-maps';
import { Bike, LocateFixed, MapPinOff } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, useSyncExternalStore, type ReactNode } from 'react';
import * as Sentry from '@sentry/react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';
import { SHEOUT_MAP_STYLE } from '../lib/mapStyle';
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
 * Positions are never animated or predicted. A marker sits exactly where the
 * caller last said it was, so a partner is only ever drawn somewhere she
 * really reported being. With a polling caller that means the marker steps
 * on each poll; that is honest, and smoothing it would draw her where she has
 * not been.
 * <p>
 * If the map cannot load - no key in this build, the key refused for this
 * site, the network down - the space shows a plain "map unavailable" panel
 * rather than Google's grey error box. Every screen that has a map still
 * says everything important in text beside it.
 */
export function LiveMap(props: LiveMapProps) {
  const heightClass = cn('h-64 w-full overflow-hidden rounded-card', props.className);
  if (!API_KEY) return <MapUnavailable className={heightClass} />;
  return (
    <APIProvider apiKey={API_KEY}>
      <LoadedMap {...props} className={heightClass} />
    </APIProvider>
  );
}

function LoadedMap({ markers, route, className, autoFit = true, onPick, center, zoom }: LiveMapProps & { className: string }) {
  const status = useApiLoadingStatus();
  const authFailed = useMapsAuthFailed();
  const [initialCenter] = useState(() => center ?? (markers[0] ? { lat: markers[0].lat, lng: markers[0].lng } : HYDERABAD));
  const [initialZoom] = useState(() => zoom ?? (center || markers[0] ? 14 : 11));

  if (authFailed || status === APILoadingStatus.AUTH_FAILURE || status === APILoadingStatus.FAILED) {
    return <MapUnavailable className={className} />;
  }

  return (
    <div className={cn('relative', className)} data-testid="live-map">
      <GoogleMap
        defaultCenter={initialCenter}
        defaultZoom={initialZoom}
        mapId={MAP_ID}
        styles={MAP_ID ? undefined : SHEOUT_MAP_STYLE}
        disableDefaultUI
        scaleControl={false}
        clickableIcons={false}
        keyboardShortcuts={false}
        gestureHandling="greedy"
        // Map loads are what Google bills for. Reusing the instance between
        // screens makes going back and forth one load, not one per visit.
        reuseMaps
        onClick={onPick ? (e) => e.detail.latLng && onPick(e.detail.latLng.lat, e.detail.latLng.lng) : undefined}
        className="h-full w-full"
      >
        {route && route.length >= 2 && (
          <Polyline
            path={route}
            strokeColor={colors.primary}
            strokeOpacity={0.85}
            strokeWeight={5}
            // Under the markers: the line is context, the pin it ends at is
            // the thing being looked for.
            zIndex={1}
            clickable={false}
          />
        )}
        <Markers markers={markers} draggable={Boolean(onPick)} onPick={onPick} />
        <CameraControl markers={markers} autoFit={autoFit} center={center} zoom={zoom} />
      </GoogleMap>
    </div>
  );
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
function CameraControl({ markers, autoFit, center, zoom }: { markers: MapMarker[]; autoFit: boolean; center?: RoutePoint; zoom?: number }) {
  const map = useMap();
  const [userMoved, setUserMoved] = useState(false);
  const [fitNonce, setFitNonce] = useState(0);
  const fitPoints = markers.filter((m) => m.kind !== 'nearby');
  const signature = fitPoints.map((m) => `${m.lat.toFixed(5)},${m.lng.toFixed(5)}`).join('|');

  useEffect(() => {
    if (!map) return;
    const listener = map.addListener('dragstart', () => setUserMoved(true));
    return () => listener.remove();
  }, [map]);

  useEffect(() => {
    if (!map || !autoFit || userMoved || fitPoints.length === 0) return;
    if (fitPoints.length === 1) {
      map.panTo(fitPoints[0]);
      if ((map.getZoom() ?? 0) < 14) map.setZoom(15);
      return;
    }
    const bounds = new google.maps.LatLngBounds();
    fitPoints.forEach((p) => bounds.extend(p));
    map.fitBounds(bounds, 48);
    // fitBounds has no maxZoom; two points a few metres apart would
    // otherwise zoom to street-furniture level.
    const once = google.maps.event.addListenerOnce(map, 'idle', () => {
      if ((map.getZoom() ?? 0) > 16) map.setZoom(16);
    });
    return () => once.remove();
    // signature stands in for fitPoints; see the comment above.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, autoFit, userMoved, signature, fitNonce]);

  // A picker's opening view. Keyed on the coordinates, so a caller
  // re-rendering with the same place does not yank the view mid-pan.
  useEffect(() => {
    if (!map || !center) return;
    map.setCenter(center);
    map.setZoom(zoom ?? 14);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, center?.lat, center?.lng, zoom]);

  if (!autoFit || !userMoved || fitPoints.length === 0) return null;
  return (
    <RecentreButton
      onClick={() => {
        setUserMoved(false);
        setFitNonce((n) => n + 1);
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

function Markers({ markers, draggable, onPick }: { markers: MapMarker[]; draggable: boolean; onPick?: (lat: number, lng: number) => void }) {
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
      out.set(m.key, typeof m.heading === 'number' && Number.isFinite(m.heading) ? m.heading : bearing);
    }
    return out;
  }, [markers]);

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
        >
          <MarkerGlyph kind={m.kind} heading={bearings.get(m.key) ?? null} />
        </HtmlMarker>
      ))}
    </>
  );
}

function MarkerGlyph({ kind, heading }: { kind: MapMarker['kind']; heading: number | null }) {
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
        <Bike className="h-4 w-4" strokeWidth={2.25} />
      </span>
    );
  }
  // The partner: a bike badge, with a small pointer on its rim that turns to
  // face her direction of travel. The bike itself stays upright - a side-on
  // bike rotated to face south would be drawn upside down.
  return (
    <span className="relative flex h-10 w-10 items-center justify-center" data-testid="driver-marker" data-heading={heading ?? ''}>
      {heading != null && (
        <span className="absolute inset-0" style={{ transform: `rotate(${heading}deg)`, transition: 'transform 300ms ease-out' }} aria-hidden="true">
          <span
            className="absolute left-1/2 top-[-7px] h-0 w-0 -translate-x-1/2"
            style={{ borderLeft: '6px solid transparent', borderRight: '6px solid transparent', borderBottom: `9px solid ${colors.primaryDark}` }}
          />
        </span>
      )}
      <span
        className="flex h-10 w-10 items-center justify-center rounded-full border-[3px] border-white text-white"
        style={{ background: colors.primary, boxShadow: '0 2px 6px rgba(36,26,51,.4)' }}
      >
        <Bike className="h-5 w-5" strokeWidth={2.25} />
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
  children,
}: {
  position: RoutePoint;
  anchor: 'center' | 'bottom';
  zIndex: number;
  draggable: boolean;
  onDragEnd?: (lat: number, lng: number) => void;
  title?: string;
  children: ReactNode;
}) {
  const map = useMap();
  const [container] = useState(() => document.createElement('div'));
  const overlayRef = useRef<google.maps.OverlayView | null>(null);
  const positionRef = useRef<RoutePoint>(position);
  const dragRef = useRef<RoutePoint | null>(null);
  positionRef.current = position;

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

function metresBetween(a: RoutePoint, b: RoutePoint): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371000 * Math.asin(Math.sqrt(h));
}

/** Initial bearing from a to b, degrees clockwise from north. */
function bearingBetween(a: RoutePoint, b: RoutePoint): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const y = Math.sin(toRad(b.lng - a.lng)) * Math.cos(toRad(b.lat));
  const x = Math.cos(toRad(a.lat)) * Math.sin(toRad(b.lat))
    - Math.sin(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.cos(toRad(b.lng - a.lng));
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
}
