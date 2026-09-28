import { ArrowLeft, MapPinned, Pencil } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode, type PointerEvent } from 'react';
import { LiveMap, useTranslation } from '@sheout/design-system';
import type { MapMarker, RoutePoint } from '@sheout/design-system';
import type { GeoAddress } from '../api/types';

/** The draggable handle and the sheet's own padding, above and below the summary. */
const SHEET_CHROME = 40;
/** How tall the sheet may grow, as a share of the screen. */
const EXPANDED_SHARE = 0.78;
/** Clear space for the floating pickup/drop chips when the view fits the route. */
const FIT_PADDING = { top: 150, right: 40, bottom: 40, left: 40 };

export interface BookingMapLayoutProps {
  title: string;
  onBack: () => void;
  pickup: GeoAddress | null;
  drop: GeoAddress | null;
  /** What the pickup chip says while there is no pickup yet (finding her location, or "tap to choose"). */
  pickupPlaceholder: string;
  onEdit: (field: 'pickup' | 'drop', mode: 'search' | 'map') => void;
  markers: MapMarker[];
  route?: RoutePoint[];
  /** Always visible in the sheet: the fare, the times and the button. */
  summary: ReactNode;
  /** Shown when the sheet is pulled up: the details. */
  more?: ReactNode;
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
  markers,
  route,
  summary,
  more,
}: BookingMapLayoutProps) {
  const { t } = useTranslation();
  const summaryRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [summaryHeight, setSummaryHeight] = useState(220);
  const [contentHeight, setContentHeight] = useState(220);
  const [viewport, setViewport] = useState(() => (typeof window === 'undefined' ? 800 : window.innerHeight));
  const [expanded, setExpanded] = useState(false);
  /** The sheet's height while a finger is dragging it; null when it rests at a snap point. */
  const [dragHeight, setDragHeight] = useState<number | null>(null);
  const drag = useRef<{ startY: number; startHeight: number; moved: boolean } | null>(null);

  // The sheet sizes itself to what is in it: the summary when down, all of
  // it (up to most of the screen) when up.
  useLayoutEffect(() => {
    const measure = () => {
      if (summaryRef.current) setSummaryHeight(summaryRef.current.offsetHeight);
      if (contentRef.current) setContentHeight(contentRef.current.scrollHeight);
    };
    measure();
    const observer = new ResizeObserver(measure);
    if (summaryRef.current) observer.observe(summaryRef.current);
    if (contentRef.current) observer.observe(contentRef.current);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const onResize = () => setViewport(window.innerHeight);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const collapsed = Math.min(summaryHeight + SHEET_CHROME, viewport * EXPANDED_SHARE);
  const full = Math.max(collapsed, Math.min(contentHeight + SHEET_CHROME, viewport * EXPANDED_SHARE));
  const canExpand = full - collapsed > 12;
  const height = dragHeight ?? (expanded && canExpand ? full : collapsed);

  function onPointerDown(e: PointerEvent<HTMLDivElement>) {
    drag.current = { startY: e.clientY, startHeight: height, moved: false };
    e.currentTarget.setPointerCapture(e.pointerId);
  }
  function onPointerMove(e: PointerEvent<HTMLDivElement>) {
    if (!drag.current) return;
    const dy = e.clientY - drag.current.startY;
    if (Math.abs(dy) > 4) drag.current.moved = true;
    if (drag.current.moved) setDragHeight(Math.min(full, Math.max(collapsed, drag.current.startHeight - dy)));
  }
  function onPointerUp() {
    const d = drag.current;
    drag.current = null;
    if (!d) return;
    if (!d.moved) {
      setExpanded((x) => !x);
    } else if (dragHeight !== null) {
      setExpanded(dragHeight > (collapsed + full) / 2);
    }
    setDragHeight(null);
  }

  return (
    <div className="fixed inset-0 z-10 mx-auto flex max-w-md flex-col bg-surface" data-testid="booking-map-layout">
      {/* The map: everything above the sheet, and nothing below it. */}
      <div className="relative min-h-0 flex-1" data-testid="booking-map-area">
        <LiveMap markers={markers} route={route} fill fitPadding={FIT_PADDING} />

        <div className="pointer-events-none absolute inset-x-3 top-3 z-10 flex gap-2">
          <button
            type="button"
            onClick={onBack}
            aria-label={t('booking.back')}
            className="pointer-events-auto flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-surface text-text-primary shadow-float"
          >
            <ArrowLeft className="h-5 w-5" aria-hidden="true" />
          </button>
          <div className="pointer-events-auto min-w-0 flex-1 space-y-2">
            <p className="sr-only">{title}</p>
            <LocationChip
              dot="bg-primary"
              label={t('booking.pickup')}
              value={pickup?.label ?? pickupPlaceholder}
              empty={!pickup}
              onEdit={() => onEdit('pickup', 'search')}
              onMap={() => onEdit('pickup', 'map')}
              testId="chip-pickup"
            />
            <LocationChip
              dot="bg-accent-orange"
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

      {/* The sheet. Its top edge is where the map stops. */}
      <div
        className="relative flex-none border-t border-border bg-surface shadow-[0_-8px_24px_rgba(16,10,26,0.12)]"
        style={{ height, transition: dragHeight === null ? 'height 220ms cubic-bezier(0.22,1,0.36,1)' : undefined }}
        data-testid="booking-sheet"
        data-expanded={expanded && canExpand}
      >
        <div
          role={canExpand ? 'button' : undefined}
          tabIndex={canExpand ? 0 : -1}
          aria-expanded={canExpand ? expanded : undefined}
          aria-label={canExpand ? (expanded ? t('booking.sheetLess') : t('booking.sheetMore')) : undefined}
          onPointerDown={canExpand ? onPointerDown : undefined}
          onPointerMove={canExpand ? onPointerMove : undefined}
          onPointerUp={canExpand ? onPointerUp : undefined}
          onPointerCancel={canExpand ? onPointerUp : undefined}
          onKeyDown={(e) => {
            if (canExpand && (e.key === 'Enter' || e.key === ' ')) {
              e.preventDefault();
              setExpanded((x) => !x);
            }
          }}
          className="flex h-6 touch-none cursor-grab items-center justify-center"
          data-testid="booking-sheet-handle"
        >
          <span className={`h-1.5 w-10 rounded-full ${canExpand ? 'bg-border' : 'bg-transparent'}`} aria-hidden="true" />
        </div>
        <div className={`h-[calc(100%-1.5rem)] px-4 pb-4 ${expanded ? 'overflow-y-auto' : 'overflow-hidden'}`}>
          <div ref={contentRef} className="space-y-4">
            <div ref={summaryRef} className="space-y-3">
              {summary}
            </div>
            {more && <div className="space-y-3" data-testid="booking-sheet-more">{more}</div>}
          </div>
        </div>
      </div>
    </div>
  );
}

function LocationChip({
  dot,
  label,
  value,
  empty,
  onEdit,
  onMap,
  testId,
}: {
  dot: string;
  label: string;
  value: string;
  empty: boolean;
  onEdit: () => void;
  onMap: () => void;
  testId: string;
}) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-1 rounded-full bg-surface py-1 pl-4 pr-1 shadow-float" data-testid={testId}>
      <button type="button" onClick={onEdit} className="flex min-w-0 flex-1 items-center gap-3 py-2 text-left">
        <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${dot}`} aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className="sr-only">{label}: </span>
          <span className={`block truncate text-sm ${empty ? 'text-text-secondary' : 'font-semibold text-text-primary'}`}>{value}</span>
        </span>
        <Pencil className="h-4 w-4 shrink-0 text-text-secondary" aria-hidden="true" />
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
