import { ArrowLeft, LocateFixed, MapPinned, Pencil, Search } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode, type PointerEvent, type RefObject } from 'react';
import { Button, LiveMap, useTranslation } from '@sheout/design-system';
import type { MapMarker, RoutePoint } from '@sheout/design-system';
import type { GeoAddress } from '../api/types';
import { CITY_CENTRE, currentPosition, isInServiceArea, outOfAreaMessage } from '../lib/geocode';
import { useCenterPinAddress } from '../lib/useCenterPinAddress';
import { useCloseOnBack } from '../lib/useGoBack';

/** Street zoom: gates, lanes and building names readable while she places the pin. */
const PIN_ZOOM = 17;

/**
 * Setting one end of the trip by moving the map under a pin fixed at its
 * centre - on THIS map, the booking screen's own. There used to be a second,
 * smaller map inside the picker sheet for this, under the first one, and a
 * pin she had to tap or drag on it: two maps on one screen for one choice.
 */
export interface PinMode {
  field: 'pickup' | 'drop';
  /** Where the pin starts, with its name. Null starts at `fallbackCentre` and looks the address up. */
  start: GeoAddress | null;
  fallbackCentre?: { lat: number; lng: number } | null;
  /** The place she searched for is an area, not a spot: she is asked to put the pin on the gate. */
  areaHint?: boolean;
  onConfirm: (address: GeoAddress) => void;
  onCancel: () => void;
  /** Back to typing an address instead. */
  onSearch: () => void;
}

/** The handle's row: a 44 px touch target, so a thumb finds it. */
const HANDLE_HEIGHT = 44;
/** Under the summary, above the details' divider. */
const SUMMARY_GAP = 8;
/** Above and below the summary when the sheet cannot be pulled up (no handle). */
const BARE_CHROME = 16;
/** Padding under the details when the sheet is up. */
const MORE_PADDING = 16;
/** A finger has to move this far before a touch is a drag rather than a tap. */
const DRAG_SLOP = 6;
/** A flick this fast (px per ms) settles the sheet in its direction, wherever it was let go. */
const FLICK_SPEED = 0.45;
/** How tall the sheet may grow, as a share of the screen. */
const EXPANDED_SHARE = 0.78;
/** Space kept clear round the route when the view fits it; the top also clears the chips. */
const FIT_PADDING = { right: 40, bottom: 40, left: 40 };
/** Below the chips' bottom edge, before the route may start. */
const CHIPS_CLEARANCE = 34;

export interface BookingMapLayoutProps {
  title: string;
  onBack: () => void;
  pickup: GeoAddress | null;
  drop: GeoAddress | null;
  /** What the pickup chip says while there is no pickup yet (finding her location, or "tap to choose"). */
  pickupPlaceholder: string;
  onEdit: (field: 'pickup' | 'drop', mode: 'search' | 'map') => void;
  /** The chip whose picker is open, drawn lit up; null when neither is. */
  active?: 'pickup' | 'drop' | null;
  /** The two chips, so the picker can open as a sheet beneath them. */
  chipsRef?: RefObject<HTMLDivElement>;
  markers: MapMarker[];
  route?: RoutePoint[];
  /** Always visible in the sheet: the fare, the times and the button. */
  summary: ReactNode;
  /** Shown when the sheet is pulled up: the details. */
  more?: ReactNode;
  /** Set while she is choosing one end on the map - see PinMode. */
  pin?: PinMode | null;
}

/**
 * Booking with the map as the screen: a full, live map (pan, pinch, the
 * route and the brand's pins exactly as elsewhere), pickup and drop as chips
 * floating on it, and a sheet at the bottom with the fare and the button.
 * <p>
 * THE MAP ENDS WHERE THE SHEET BEGINS - it is never underneath it. Google's
 * logo and "Terms" link sit on the map's own bottom edge, and Google's terms
 * forbid covering them. Native ride apps keep them visible by padding the
 * map, which lifts the logo above their sheet; the Maps JavaScript API used
 * here offers no such padding for the logo, so the same result is reached
 * the only compliant way: map and sheet share one column, and as the sheet
 * is pulled up the map gets shorter, carrying its logo up with it. That is
 * also why the sheet's top edge is square - rounded corners would have to
 * overlap the map to look right, and the logo lives in that corner.
 */
export function BookingMapLayout({
  title,
  onBack,
  pickup,
  drop,
  pickupPlaceholder,
  onEdit,
  active = null,
  chipsRef,
  markers,
  route,
  summary: tripSummary,
  more: tripMore,
  pin = null,
}: BookingMapLayoutProps) {
  const { t } = useTranslation();
  const pinState = useCenterPinAddress(pin?.start ?? null, pin !== null);
  /** Where the map is sent while choosing on it: the start, then wherever "locate me" found her. */
  const [pinCentre, setPinCentre] = useState<{ lat: number; lng: number } | null>(null);
  const [pinNonce, setPinNonce] = useState(0);
  const [locating, setLocating] = useState(false);
  const [locateError, setLocateError] = useState<string | null>(null);
  const pinField = pin?.field ?? null;

  async function locateMe() {
    setLocating(true);
    setLocateError(null);
    try {
      const here = await currentPosition();
      setPinCentre(here);
      setPinNonce((n) => n + 1);
    } catch (err) {
      setLocateError((err as Error).message);
    } finally {
      setLocating(false);
    }
  }

  useEffect(() => {
    if (!pin) return;
    setLocateError(null);
    setExpanded(false);
    setPinCentre(pin.start ? { lat: pin.start.lat, lng: pin.start.lng } : pin.fallbackCentre ?? CITY_CENTRE);
    setPinNonce((n) => n + 1);
    // A pickup with nowhere to start from starts where she is.
    if (!pin.start && pin.field === 'pickup') void locateMe();
    // Each opening, not each re-render of the same one.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pinField]);

  // The phone's back button leaves the pin, not the booking.
  useCloseOnBack(pin !== null, () => pin?.onCancel());

  const pinAddress = pinState.address;
  const pinBusy = pinState.moving || pinState.resolving;
  const pinOutside = pinAddress != null && !isInServiceArea(pinAddress);
  const summary = pin ? (
    <div className="space-y-3" data-testid="pin-sheet">
      <p className="text-xs font-semibold uppercase tracking-wide text-text-secondary">
        {pin.field === 'pickup' ? t('picker.pickupLocation') : t('picker.dropLocation')}
      </p>
      <div className="flex items-start gap-3">
        <span className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${pin.field === 'pickup' ? 'bg-primary' : 'bg-accent-orange'}`} aria-hidden="true" />
        <p
          className={`min-h-[2.5rem] flex-1 text-sm font-semibold leading-snug text-text-primary transition-opacity ${pinBusy ? 'opacity-50' : ''}`}
          aria-live="polite"
          data-testid="pin-address"
        >
          {pinState.moving
            ? t('picker.movingPin')
            : pinState.resolving
              ? t('picker.lookingUp')
              : pinAddress?.label ?? (pinState.error ? t('picker.cannotName') : t('picker.lookingUp'))}
        </p>
      </div>
      {pin.areaHint && <p className="rounded-input bg-primary-light px-3 py-2 text-xs text-primary" data-testid="picker-area-hint">{t('picker.areaHint')}</p>}
      {pinState.error && !pinBusy && (
        <div className="flex items-center justify-between gap-3">
          <p className="text-xs text-danger">{pinState.error}</p>
          <button type="button" onClick={pinState.retry} className="shrink-0 text-xs font-semibold text-primary">
            {t('common.tryAgain')}
          </button>
        </div>
      )}
      {pinOutside && !pinBusy && <p className="text-xs font-medium text-danger">{outOfAreaMessage()}</p>}
      {locateError && <p className="text-xs text-text-secondary">{locateError}</p>}
      <Button
        fullWidth
        disabled={!pinAddress || pinBusy || pinOutside || Boolean(pinState.error)}
        onClick={() => pinAddress && pin.onConfirm(pinAddress)}
        data-testid="pin-confirm"
      >
        {pin.field === 'pickup' ? t('picker.confirmPickup') : t('picker.confirmDrop')}
      </Button>
      <button
        type="button"
        onClick={pin.onSearch}
        className="flex w-full items-center justify-center gap-2 py-1 text-sm font-semibold text-primary"
        data-testid="pin-search-instead"
      >
        <Search className="h-4 w-4" aria-hidden="true" />
        {t('picker.searchInstead')}
      </button>
    </div>
  ) : (
    tripSummary
  );
  const more = pin ? undefined : tripMore;
  const ownChipsRef = useRef<HTMLDivElement>(null);
  const chips = chipsRef ?? ownChipsRef;
  /** The chips grow to two lines for a long address, so the route clears their real height. */
  const [chipsBottom, setChipsBottom] = useState(116);
  const sheetRef = useRef<HTMLDivElement>(null);
  const summaryRef = useRef<HTMLDivElement>(null);
  const moreRef = useRef<HTMLDivElement>(null);
  const moreInnerRef = useRef<HTMLDivElement>(null);
  const [summaryHeight, setSummaryHeight] = useState(220);
  const [moreHeight, setMoreHeight] = useState(0);
  const [viewport, setViewport] = useState(() => (typeof window === 'undefined' ? 800 : window.innerHeight));
  const [expanded, setExpanded] = useState(false);
  /** The sheet's height while a finger is dragging it; null when it rests at a snap point. */
  const [dragHeight, setDragHeight] = useState<number | null>(null);
  const drag = useRef<{
    pointerId: number | null;
    startY: number;
    startHeight: number;
    moved: boolean;
    onHandle: boolean;
    lastY: number;
    lastT: number;
    speed: number;
  } | null>(null);
  /** Set when a drag ends, so the click that follows does not press whatever the finger was on. */
  const swallowClick = useRef(false);

  // The sheet sizes itself to what is in it: the summary when down, the
  // summary and the details (up to most of the screen) when up.
  useLayoutEffect(() => {
    const measure = () => {
      if (summaryRef.current) setSummaryHeight(summaryRef.current.offsetHeight);
      setMoreHeight(moreInnerRef.current ? moreInnerRef.current.offsetHeight : 0);
    };
    measure();
    const observer = new ResizeObserver(measure);
    if (summaryRef.current) observer.observe(summaryRef.current);
    if (moreInnerRef.current) observer.observe(moreInnerRef.current);
    return () => observer.disconnect();
  }, [more !== undefined]);

  useLayoutEffect(() => {
    const el = chips.current;
    if (!el) return;
    const measure = () => {
      const mapTop = el.closest('[data-testid=booking-map-area]')?.getBoundingClientRect().top ?? 0;
      setChipsBottom(Math.round(el.getBoundingClientRect().bottom - mapTop));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [chips]);

  useEffect(() => {
    const onResize = () => setViewport(window.innerHeight);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const canExpand = more !== undefined && moreHeight > 12;
  const chrome = canExpand ? HANDLE_HEIGHT + SUMMARY_GAP : BARE_CHROME;
  const collapsed = Math.min(summaryHeight + chrome, viewport * EXPANDED_SHARE);
  const full = canExpand ? Math.max(collapsed, Math.min(summaryHeight + chrome + moreHeight, viewport * EXPANDED_SHARE)) : collapsed;
  const isUp = expanded && canExpand;
  const height = dragHeight ?? (isUp ? full : collapsed);

  // A drag moves the sheet between its two heights; where it is let go, or
  // the direction of a flick, decides which one it settles at.
  function beginDrag(pointerId: number | null, y: number, onHandle: boolean) {
    drag.current = { pointerId, startY: y, startHeight: height, moved: false, onHandle, lastY: y, lastT: performance.now(), speed: 0 };
  }
  /** True once the finger has travelled far enough to be a drag. */
  function moveDrag(y: number): boolean {
    const d = drag.current;
    if (!d) return false;
    const dy = y - d.startY;
    if (!d.moved && Math.abs(dy) < DRAG_SLOP) return false;
    if (!d.moved && sheetRef.current && d.pointerId !== null) {
      // Captured only now: capturing on press would steal a plain tap's click.
      try {
        sheetRef.current.setPointerCapture(d.pointerId);
      } catch {
        // The pointer has already gone; the drag simply ends.
      }
    }
    d.moved = true;
    const now = performance.now();
    if (now > d.lastT) d.speed = (y - d.lastY) / (now - d.lastT);
    d.lastY = y;
    d.lastT = now;
    setDragHeight(Math.min(full, Math.max(collapsed, d.startHeight - dy)));
    return true;
  }
  function endDrag() {
    const d = drag.current;
    drag.current = null;
    if (!d) return;
    if (!d.moved) {
      // A tap on the handle opens or closes the sheet, for anyone who does not drag.
      if (d.onHandle && canExpand) setExpanded((x) => !x);
    } else {
      const at = d.startHeight - (d.lastY - d.startY);
      if (Math.abs(d.speed) >= FLICK_SPEED) setExpanded(d.speed < 0);
      else setExpanded(at > (collapsed + full) / 2);
      swallowClick.current = true;
      window.setTimeout(() => {
        swallowClick.current = false;
      }, 0);
    }
    setDragHeight(null);
  }

  // Pointer drags: anywhere on the sheet except text fields, and except the
  // details while they are scrollable - a finger there scrolls them (see the
  // touch handlers below). A mouse drags the sheet from anywhere.
  function onPointerDown(e: PointerEvent<HTMLDivElement>) {
    if (!canExpand || (e.pointerType === 'mouse' && e.button !== 0)) return;
    const target = e.target as HTMLElement;
    if (target.closest('input, textarea, select, [contenteditable="true"]')) return;
    if (isUp && e.pointerType !== 'mouse' && moreRef.current?.contains(target)) return;
    beginDrag(e.pointerId, e.clientY, Boolean(target.closest('[data-testid="booking-sheet-handle"]')));
  }
  function onPointerMove(e: PointerEvent<HTMLDivElement>) {
    if (drag.current && drag.current.pointerId === e.pointerId) moveDrag(e.clientY);
  }
  function onPointerEnd(e: PointerEvent<HTMLDivElement>) {
    if (drag.current && drag.current.pointerId === e.pointerId) endDrag();
  }

  // The details, once up, scroll like any list. Pulled down while already at
  // their top, the finger takes the sheet down instead - the way native ride
  // apps behave. Touch events, not pointer events: only a non-passive
  // touchmove can stop the browser scrolling (or pulling to refresh) once a
  // finger is already down.
  useEffect(() => {
    const el = moreRef.current;
    if (!el || !isUp) return;
    let startY = 0;
    let atTop = false;
    let mine = false;
    const onStart = (e: TouchEvent) => {
      startY = e.touches[0].clientY;
      atTop = el.scrollTop <= 0;
      mine = false;
    };
    const onMove = (e: TouchEvent) => {
      const y = e.touches[0].clientY;
      if (!mine && atTop && y - startY > DRAG_SLOP) {
        mine = true;
        beginDrag(null, startY, false);
      }
      if (mine) {
        e.preventDefault();
        moveDrag(y);
      }
    };
    const onEnd = () => {
      if (mine) endDrag();
      mine = false;
    };
    el.addEventListener('touchstart', onStart, { passive: true });
    el.addEventListener('touchmove', onMove, { passive: false });
    el.addEventListener('touchend', onEnd);
    el.addEventListener('touchcancel', onEnd);
    return () => {
      el.removeEventListener('touchstart', onStart);
      el.removeEventListener('touchmove', onMove);
      el.removeEventListener('touchend', onEnd);
      el.removeEventListener('touchcancel', onEnd);
    };
    // The handlers read the latest heights through the closure of this render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isUp, collapsed, full]);

  // The sheet opens on its details from the top, every time.
  useEffect(() => {
    if (!isUp && moreRef.current) moreRef.current.scrollTop = 0;
  }, [isUp]);

  return (
    <div className="fixed inset-0 z-10 mx-auto flex max-w-md flex-col bg-surface" data-testid="booking-map-layout">
      {/* The map: everything above the sheet, and nothing below it. */}
      <div className="relative min-h-0 flex-1" data-testid="booking-map-area">
        <LiveMap
          markers={pin ? markers.filter((m) => m.kind !== pin.field) : markers}
          route={pin ? undefined : route}
          fill
          autoFit={!pin}
          center={pin ? pinCentre ?? undefined : undefined}
          zoom={pin ? PIN_ZOOM : undefined}
          centerNonce={pinNonce}
          fitPadding={{ ...FIT_PADDING, top: chipsBottom + CHIPS_CLEARANCE }}
          centerPin={
            pin
              ? {
                  kind: pin.field,
                  label: pin.field === 'pickup' ? t('picker.pickupHere') : t('picker.dropHere'),
                  onMoveStart: pinState.onMoveStart,
                  onIdle: pinState.onIdle,
                }
              : undefined
          }
        />

        {pin && (
          <>
            <div className="pointer-events-none absolute inset-x-3 top-3 z-10 flex items-center gap-2">
              <button
                type="button"
                onClick={pin.onCancel}
                aria-label={t('booking.back')}
                className="pointer-events-auto flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-surface text-text-primary shadow-float"
                data-testid="pin-back"
              >
                <ArrowLeft className="h-5 w-5" aria-hidden="true" />
              </button>
              <p className="min-w-0 flex-1 rounded-full bg-surface px-4 py-2.5 text-sm font-semibold text-text-primary shadow-float">
                {pin.field === 'pickup' ? t('picker.movePickup') : t('picker.moveDrop')}
              </p>
            </div>
            {/* Above Google's logo and Terms, which sit along the map's bottom edge. */}
            <button
              type="button"
              onClick={() => void locateMe()}
              disabled={locating}
              aria-label={t('picker.locateMe')}
              title={t('picker.locateMe')}
              className="absolute bottom-8 right-3 z-10 flex h-11 w-11 items-center justify-center rounded-full bg-surface text-primary shadow-float disabled:opacity-60"
              data-testid="pin-locate"
            >
              <LocateFixed className={`h-5 w-5 ${locating ? 'motion-safe:animate-pulse' : ''}`} aria-hidden="true" />
            </button>
          </>
        )}

        <div className={`pointer-events-none absolute inset-x-3 top-3 z-10 flex gap-2 ${pin ? 'hidden' : ''}`}>
          <button
            type="button"
            onClick={onBack}
            aria-label={t('booking.back')}
            className="pointer-events-auto flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-surface text-text-primary shadow-float"
          >
            <ArrowLeft className="h-5 w-5" aria-hidden="true" />
          </button>
          <div ref={chips} className="pointer-events-auto min-w-0 flex-1 space-y-2">
            <p className="sr-only">{title}</p>
            <LocationChip
              tone="pickup"
              active={active === 'pickup'}
              label={t('booking.pickup')}
              value={pickup?.label ?? pickupPlaceholder}
              empty={!pickup}
              onEdit={() => onEdit('pickup', 'search')}
              onMap={() => onEdit('pickup', 'map')}
              testId="chip-pickup"
            />
            <LocationChip
              tone="drop"
              active={active === 'drop'}
              label={t('booking.drop')}
              value={drop?.label ?? t('booking.selectDestination')}
              empty={!drop}
              onEdit={() => onEdit('drop', 'search')}
              onMap={() => onEdit('drop', 'map')}
              testId="chip-drop"
            />
          </div>
        </div>
      </div>

      {/* The sheet. Its top edge is where the map stops. The whole sheet
          drags, not only its handle: a thumb lands on the fare, not on a
          4 px bar. touch-none everywhere but the scrollable details, so the
          browser never scrolls the page or pulls to refresh mid-drag. */}
      <div
        ref={sheetRef}
        className={`relative flex flex-none flex-col overscroll-contain border-t border-border bg-surface shadow-[0_-8px_24px_rgba(16,10,26,0.12)] ${canExpand ? 'touch-none' : ''}`}
        style={{ height, transition: dragHeight === null ? 'height 220ms cubic-bezier(0.22,1,0.36,1)' : undefined }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerEnd}
        onPointerCancel={onPointerEnd}
        onClickCapture={(e) => {
          if (swallowClick.current) {
            e.preventDefault();
            e.stopPropagation();
          }
        }}
        data-testid="booking-sheet"
        data-expanded={isUp}
      >
        {canExpand ? (
          <div
            role="button"
            tabIndex={0}
            aria-expanded={isUp}
            aria-label={isUp ? t('booking.sheetLess') : t('booking.sheetMore')}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                setExpanded((x) => !x);
              }
            }}
            className="flex flex-none cursor-grab items-center justify-center"
            style={{ height: HANDLE_HEIGHT }}
            data-testid="booking-sheet-handle"
          >
            <span className="h-1.5 w-10 rounded-full bg-border" aria-hidden="true" />
          </div>
        ) : (
          <div className="flex-none" style={{ height: BARE_CHROME / 2 }} aria-hidden="true" />
        )}
        {/* The summary never scrolls away: what she is booking, its price and
            the Book button stay in view whether the sheet is up or down. */}
        <div ref={summaryRef} className="flex-none space-y-3 px-4" style={{ paddingBottom: canExpand ? SUMMARY_GAP : BARE_CHROME / 2 }}>
          {summary}
        </div>
        {more && (
          <div
            ref={moreRef}
            className={`min-h-0 flex-1 overscroll-contain px-4 ${isUp ? 'touch-pan-y overflow-y-auto' : 'overflow-hidden'}`}
            aria-hidden={!isUp}
            data-testid="booking-sheet-more"
          >
            <div ref={moreInnerRef} className="space-y-3 border-t border-border pt-3" style={{ paddingBottom: MORE_PADDING }}>
              {more}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Each chip's colour is its pin's: purple for pickup, orange for drop. At
 * rest only the dot carries it; while its picker is open the whole chip
 * does - a tinted fill, a ring and a glow in that colour, and a solid pencil
 * - so it is plain which end is being set.
 */
const CHIP_TONES = {
  pickup: {
    dot: 'bg-primary',
    active: 'bg-primary-light ring-2 ring-primary shadow-[0_0_0_6px_rgb(var(--c-primary)/0.16),var(--elev-float)]',
    pencil: 'text-primary',
  },
  drop: {
    dot: 'bg-accent-orange',
    active:
      'bg-accent-orange-tint ring-2 ring-accent-orange shadow-[0_0_0_6px_rgb(var(--c-accent-orange)/0.24),var(--elev-float)]',
    pencil: 'text-accent-orange-strong',
  },
} as const;

function LocationChip({
  tone,
  active,
  label,
  value,
  empty,
  onEdit,
  onMap,
  testId,
}: {
  tone: keyof typeof CHIP_TONES;
  active: boolean;
  label: string;
  value: string;
  empty: boolean;
  onEdit: () => void;
  onMap: () => void;
  testId: string;
}) {
  const { t } = useTranslation();
  const colours = CHIP_TONES[tone];
  return (
    <div
      className={`flex items-center gap-1 rounded-full py-1 pl-4 pr-1 transition-[background-color,box-shadow] duration-200 ${
        active ? colours.active : 'bg-surface shadow-float'
      }`}
      data-testid={testId}
      data-active={active}
    >
      <button type="button" onClick={onEdit} className="flex min-w-0 flex-1 items-center gap-3 py-2 text-left">
        <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${colours.dot}`} aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className="sr-only">{label}: </span>
          {/* An address: up to two lines, then an ellipsis - the chip is the
              only place on this screen the address is written, so one line
              cut off most Hyderabad addresses mid-locality. A prompt ("Choose
              pickup point"): one line, and an ellipsis rather than a wrap if
              a translation is ever too long for a 360 px screen. */}
          <span
            title={value}
            className={`text-sm leading-snug ${empty ? 'block truncate text-text-secondary' : 'line-clamp-2 break-words font-semibold text-text-primary'}`}
          >
            {value}
          </span>
        </span>
        <Pencil
          className={`h-4 w-4 shrink-0 ${active ? colours.pencil : 'text-text-secondary'}`}
          fill={active ? 'currentColor' : 'none'}
          aria-hidden="true"
        />
      </button>
      <button
        type="button"
        onClick={onMap}
        aria-label={t('booking.pinOnMap', { place: label })}
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-primary"
      >
        <MapPinned className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}
