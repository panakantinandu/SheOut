import { useMemo, useRef } from 'react';
import type { RouteStep, TripRoute } from '../api/types';

/**
 * In-app turn-by-turn navigation: the arithmetic.
 * <p>
 * The route (OSRM's line and turns, from the trip route endpoint) is fixed;
 * her GPS fix moves. For each fix this works out where she is ALONG the
 * route, how far she is from it, which turn is next and how far away it is,
 * and what is left to drive. Everything the navigation screen shows and says
 * comes from here.
 * <p>
 * All in a flat local projection (metres east and north of the route's first
 * point). Across a city a flat projection is out by well under a metre per
 * kilometre, far below GPS error, and it turns the geometry into plain 2-D
 * sums that run on every fix without any cost.
 */

export interface LatLng {
  lat: number;
  lng: number;
}

interface XY {
  x: number;
  y: number;
}

export interface PreparedRoute {
  points: LatLng[];
  steps: RouteStep[];
  /** Metres along the route at each point. */
  along: number[];
  /** Metres along the route at each step's manoeuvre. */
  stepAlong: number[];
  totalMetres: number;
  totalSeconds: number;
  xy: XY[];
  origin: LatLng;
  cosLat: number;
}

export interface NavState {
  /** Metres driven along the route. */
  alongMetres: number;
  /** How far her fix is from the route line. */
  offRouteMetres: number;
  /** Index into steps of the next manoeuvre, or -1 when all that is left is to arrive. */
  nextStep: number;
  distanceToNextMetres: number;
  /** The manoeuvre after next, when it comes soon after it ("then turn right"). */
  thenStep: number;
  remainingMetres: number;
  remainingSeconds: number;
  /** The line still to drive, starting from where she is. */
  remaining: LatLng[];
  /** Segment she was matched to, a hint for the next fix. */
  segment: number;
}

const EARTH_M = 6371000;
const rad = (d: number) => (d * Math.PI) / 180;

function toXY(p: LatLng, origin: LatLng, cosLat: number): XY {
  return { x: rad(p.lng - origin.lng) * EARTH_M * cosLat, y: rad(p.lat - origin.lat) * EARTH_M };
}

function fromXY(q: XY, origin: LatLng, cosLat: number): LatLng {
  return { lat: origin.lat + (q.y / EARTH_M) * (180 / Math.PI), lng: origin.lng + (q.x / (EARTH_M * cosLat)) * (180 / Math.PI) };
}

/** Closest point to p on segment ab: the fraction along it, and the distance. */
function onSegment(p: XY, a: XY, b: XY): { t: number; dist: number; at: XY } {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len2 = dx * dx + dy * dy;
  const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2));
  const at = { x: a.x + t * dx, y: a.y + t * dy };
  return { t, dist: Math.hypot(p.x - at.x, p.y - at.y), at };
}

export function prepareRoute(route: TripRoute | null): PreparedRoute | null {
  if (!route || route.points.length < 2) return null;
  const points = route.points;
  const origin = points[0];
  const cosLat = Math.cos(rad(origin.lat));
  const xy = points.map((p) => toXY(p, origin, cosLat));
  const along = [0];
  for (let i = 1; i < xy.length; i++) along.push(along[i - 1] + Math.hypot(xy[i].x - xy[i - 1].x, xy[i].y - xy[i - 1].y));
  const totalMetres = along[along.length - 1];

  // Where each manoeuvre sits along the line. Searched forward from the
  // previous one, so a route that doubles back past an earlier turn does not
  // put a later turn at the earlier place.
  const steps = route.steps ?? [];
  const stepAlong: number[] = [];
  let from = 0;
  for (const step of steps) {
    if (step.type === 'depart') {
      stepAlong.push(0);
      continue;
    }
    if (step.type === 'arrive') {
      stepAlong.push(totalMetres);
      continue;
    }
    const p = toXY(step, origin, cosLat);
    let best = { seg: from, t: 0, dist: Infinity };
    for (let i = from; i < xy.length - 1; i++) {
      const s = onSegment(p, xy[i], xy[i + 1]);
      if (s.dist < best.dist) best = { seg: i, t: s.t, dist: s.dist };
      if (best.dist < 1) break;
    }
    const segLen = along[best.seg + 1] - along[best.seg];
    stepAlong.push(along[best.seg] + best.t * segLen);
    from = best.seg;
  }

  return {
    points,
    steps,
    along,
    stepAlong,
    totalMetres,
    totalSeconds: (route.durationMinutes ?? totalMetres / 1000 / 22 * 60) * 60,
    xy,
    origin,
    cosLat,
  };
}

/** A manoeuvre within this distance of the next one is announced with it ("then ..."). */
const THEN_WITHIN_METRES = 150;
/** Just past a manoeuvre point still counts as at it, so GPS jitter does not flip back to it. */
const PASSED_MARGIN_METRES = 10;

export function navState(route: PreparedRoute, fix: LatLng, hint: number): NavState {
  const p = toXY(fix, route.origin, route.cosLat);
  const n = route.xy.length;

  // Near where she was last matched first: on a route that passes the same
  // junction twice, the nearest segment overall can be the wrong visit.
  const search = (lo: number, hi: number) => {
    let best = { seg: lo, t: 0, dist: Infinity, at: route.xy[lo] };
    for (let i = Math.max(0, lo); i <= Math.min(n - 2, hi); i++) {
      const s = onSegment(p, route.xy[i], route.xy[i + 1]);
      if (s.dist < best.dist) best = { seg: i, t: s.t, dist: s.dist, at: s.at };
    }
    return best;
  };
  let best = search(hint - 3, hint + 60);
  if (best.dist > 50) {
    const global = search(0, n - 2);
    if (global.dist < best.dist) best = global;
  }

  const segLen = route.along[best.seg + 1] - route.along[best.seg];
  const alongMetres = route.along[best.seg] + best.t * segLen;
  const remainingMetres = Math.max(0, route.totalMetres - alongMetres);

  let nextStep = -1;
  for (let i = 0; i < route.steps.length; i++) {
    const s = route.steps[i];
    if (s.type === 'depart') continue;
    if (route.stepAlong[i] > alongMetres + PASSED_MARGIN_METRES || s.type === 'arrive') {
      nextStep = i;
      break;
    }
  }
  const distanceToNextMetres = nextStep >= 0 ? Math.max(0, route.stepAlong[nextStep] - alongMetres) : remainingMetres;
  let thenStep = -1;
  if (nextStep >= 0 && nextStep + 1 < route.steps.length) {
    const gap = route.stepAlong[nextStep + 1] - route.stepAlong[nextStep];
    if (gap <= THEN_WITHIN_METRES && route.steps[nextStep].type !== 'arrive') thenStep = nextStep + 1;
  }

  const snapped = fromXY(best.at, route.origin, route.cosLat);
  return {
    alongMetres,
    offRouteMetres: best.dist,
    nextStep,
    distanceToNextMetres,
    thenStep,
    remainingMetres,
    remainingSeconds: route.totalMetres > 0 ? route.totalSeconds * (remainingMetres / route.totalMetres) : 0,
    remaining: [snapped, ...route.points.slice(best.seg + 1)],
    segment: best.seg,
  };
}

/** The route prepared once per route, and her state along it for each fix. */
export function useNavigation(route: TripRoute | null, fix: LatLng | null): { prepared: PreparedRoute | null; state: NavState | null } {
  const prepared = useMemo(() => prepareRoute(route), [route]);
  const hint = useRef(0);
  const lastRoute = useRef<PreparedRoute | null>(null);
  if (lastRoute.current !== prepared) {
    hint.current = 0;
    lastRoute.current = prepared;
  }
  const state = useMemo(() => {
    if (!prepared || !fix) return null;
    const s = navState(prepared, fix, hint.current);
    hint.current = s.segment;
    return s;
  }, [prepared, fix?.lat, fix?.lng]);
  return { prepared, state };
}

// ------------------------------------------------------------------ words

export type ManeuverIcon =
  | 'straight' | 'slight-left' | 'left' | 'sharp-left' | 'slight-right' | 'right' | 'sharp-right'
  | 'uturn' | 'roundabout' | 'arrive' | 'keep-left' | 'keep-right';

/**
 * Which sentence a step is, as a translation key under nav.step, plus the
 * values it takes. The key has an "Onto" variant when the road has a name,
 * because Hindi and Telugu put the road before the verb: the sentence is
 * translated whole, never assembled from fragments.
 */
export function describeStep(step: RouteStep): { key: string; values: Record<string, string | number>; icon: ManeuverIcon } {
  const road = step.name?.trim() ?? '';
  type Described = { key: string; values: Record<string, string | number>; icon: ManeuverIcon };
  const withRoad = (key: string, icon: ManeuverIcon): Described =>
    road ? { key: `${key}Onto`, values: { road }, icon } : { key, values: {}, icon };
  const m = step.modifier ?? 'straight';

  switch (step.type) {
    case 'arrive':
      return { key: 'arrive', values: {}, icon: 'arrive' };
    case 'depart':
      return withRoad('depart', 'straight');
    case 'roundabout':
    case 'rotary':
    case 'roundabout turn':
      return step.exit
        ? { key: road ? 'roundaboutExitOnto' : 'roundaboutExit', values: road ? { exit: step.exit, road } : { exit: step.exit }, icon: 'roundabout' }
        : withRoad('roundabout', 'roundabout');
    case 'fork':
    case 'on ramp':
    case 'off ramp':
      if (m.includes('left')) return withRoad('keepLeft', 'keep-left');
      if (m.includes('right')) return withRoad('keepRight', 'keep-right');
      return withRoad('continue', 'straight');
    case 'exit roundabout':
    case 'exit rotary':
    case 'new name':
    case 'continue':
    case 'notification':
      if (m === 'straight' || !step.modifier) return withRoad('continue', 'straight');
      break;
    default:
      break;
  }
  switch (m) {
    case 'uturn':
      return withRoad('uturn', 'uturn');
    case 'sharp left':
      return withRoad('sharpLeft', 'sharp-left');
    case 'left':
      return withRoad('left', 'left');
    case 'slight left':
      return withRoad('slightLeft', 'slight-left');
    case 'sharp right':
      return withRoad('sharpRight', 'sharp-right');
    case 'right':
      return withRoad('right', 'right');
    case 'slight right':
      return withRoad('slightRight', 'slight-right');
    default:
      return withRoad('continue', 'straight');
  }
}

/**
 * A distance as a driver reads it: to the nearest 10 m close up, 50 m
 * further out, then kilometres to one decimal. "In 243 m" is precision
 * nobody can use on a bike.
 */
export function roundDistance(metres: number): { unit: 'm' | 'km'; value: number } {
  if (metres >= 1000) return { unit: 'km', value: Math.round(metres / 100) / 10 };
  if (metres >= 300) return { unit: 'm', value: Math.round(metres / 50) * 50 };
  return { unit: 'm', value: Math.max(10, Math.round(metres / 10) * 10) };
}
