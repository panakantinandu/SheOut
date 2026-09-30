import type { RoutePoint } from '../components/LiveMap';

/**
 * How a partner's marker moves on the map, and how far along the road she is.
 * <p>
 * Positions arrive in steps: every GPS fix on her own phone, every poll on
 * the rider's. Drawn as they come, the bike jumps 50-100 m at a time and the
 * route never shows what is behind her. This glides the marker from where it
 * is drawn to each new fix, taking about as long as the gap between fixes,
 * so it is always moving and arrives at the newest real position just as the
 * next one is due.
 * <p>
 * WHERE SHE IS DRAWN IS STILL WHERE SHE HAS BEEN. When both fixes sit on the
 * route, the glide follows the road between them, never the straight line
 * across a corner, so every point the marker passes through is on the road
 * she was on between two real positions. Off the route, it glides straight
 * over a short hop and jumps across a long one (a gap in the data, not a
 * ride), because a straight glide across a whole block would be a guess.
 * Nothing is predicted past the last fix.
 * <p>
 * Same flat local projection as the partner app's navigation: across a city
 * it is out by far less than GPS error.
 */

interface XY {
  x: number;
  y: number;
}

export interface PreparedLine {
  points: RoutePoint[];
  xy: XY[];
  /** Metres along the line at each point. */
  along: number[];
  total: number;
  origin: RoutePoint;
  cosLat: number;
}

export interface Projection {
  along: number;
  /** Metres from the line. */
  dist: number;
  seg: number;
  at: RoutePoint;
}

const EARTH_M = 6371000;
const rad = (d: number) => (d * Math.PI) / 180;

function toXY(p: RoutePoint, origin: RoutePoint, cosLat: number): XY {
  return { x: rad(p.lng - origin.lng) * EARTH_M * cosLat, y: rad(p.lat - origin.lat) * EARTH_M };
}

function fromXY(q: XY, origin: RoutePoint, cosLat: number): RoutePoint {
  return { lat: origin.lat + (q.y / EARTH_M) * (180 / Math.PI), lng: origin.lng + (q.x / (EARTH_M * cosLat)) * (180 / Math.PI) };
}

export function prepareLine(points: RoutePoint[] | undefined): PreparedLine | null {
  if (!points || points.length < 2) return null;
  const origin = points[0];
  const cosLat = Math.cos(rad(origin.lat));
  const xy = points.map((p) => toXY(p, origin, cosLat));
  const along = [0];
  for (let i = 1; i < xy.length; i++) along.push(along[i - 1] + Math.hypot(xy[i].x - xy[i - 1].x, xy[i].y - xy[i - 1].y));
  return { points, xy, along, total: along[along.length - 1], origin, cosLat };
}

/**
 * The nearest point on the line to p. Searched near `hint` (the segment of
 * the last match) first: a route that passes the same junction twice has
 * two nearest points, and the one ahead of her is the right one.
 */
export function projectOnLine(line: PreparedLine, p: RoutePoint, hint = 0): Projection {
  const q = toXY(p, line.origin, line.cosLat);
  const n = line.xy.length;
  const search = (lo: number, hi: number) => {
    let best = { seg: Math.max(0, lo), t: 0, dist: Infinity, at: line.xy[Math.max(0, lo)] };
    for (let i = Math.max(0, lo); i <= Math.min(n - 2, hi); i++) {
      const a = line.xy[i];
      const b = line.xy[i + 1];
      const dx = b.x - a.x;
      const dy = b.y - a.y;
      const len2 = dx * dx + dy * dy;
      const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((q.x - a.x) * dx + (q.y - a.y) * dy) / len2));
      const at = { x: a.x + t * dx, y: a.y + t * dy };
      const dist = Math.hypot(q.x - at.x, q.y - at.y);
      if (dist < best.dist) best = { seg: i, t, dist, at };
    }
    return best;
  };
  let best = search(hint - 3, hint + 60);
  if (best.dist > 40) {
    const global = search(0, n - 2);
    if (global.dist < best.dist) best = global;
  }
  const segLen = line.along[best.seg + 1] - line.along[best.seg];
  return { along: line.along[best.seg] + best.t * segLen, dist: best.dist, seg: best.seg, at: fromXY(best.at, line.origin, line.cosLat) };
}

/** Index of the segment that contains `along`. */
function segmentAt(line: PreparedLine, along: number): number {
  let lo = 0;
  let hi = line.along.length - 2;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (line.along[mid] <= along) lo = mid;
    else hi = mid - 1;
  }
  return lo;
}

/** The point `along` metres down the line. */
export function pointAlong(line: PreparedLine, along: number): { point: RoutePoint; seg: number } {
  const d = Math.max(0, Math.min(line.total, along));
  const seg = segmentAt(line, d);
  const segLen = line.along[seg + 1] - line.along[seg];
  const t = segLen === 0 ? 0 : (d - line.along[seg]) / segLen;
  const a = line.xy[seg];
  const b = line.xy[seg + 1];
  return { point: fromXY({ x: a.x + t * (b.x - a.x), y: a.y + t * (b.y - a.y) }, line.origin, line.cosLat), seg };
}

/** The line cut at `along`: what is behind her, and what is still ahead. */
export function splitLine(line: PreparedLine, along: number): { done: RoutePoint[]; ahead: RoutePoint[] } {
  const { point, seg } = pointAlong(line, along);
  return {
    done: [...line.points.slice(0, seg + 1), point],
    ahead: [point, ...line.points.slice(seg + 1)],
  };
}

/** Direction of the road at `along`, degrees clockwise from north. */
export function lineBearingAt(line: PreparedLine, along: number): number {
  const seg = segmentAt(line, Math.max(0, Math.min(line.total, along)));
  const a = line.xy[seg];
  const b = line.xy[seg + 1];
  return ((Math.atan2(b.x - a.x, b.y - a.y) * 180) / Math.PI + 360) % 360;
}

export function metresBetween(a: RoutePoint, b: RoutePoint): number {
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_M * Math.asin(Math.sqrt(h));
}

/** A fix this close to the route is on it: GPS in a city is good to about this. */
export const ON_ROUTE_METRES = 35;
/** Moves smaller than this are GPS jitter from a partner standing still. */
const JITTER_METRES = 4;
/** Slipping back along the road by less than this is jitter too, not a U-turn. */
const BACKSLIDE_METRES = 20;
/** Faster than this between two fixes is a gap in the data, not riding: jump, do not glide. */
const MAX_GLIDE_SPEED_MPS = 40;
/** Glides take the gap between fixes, within these bounds. */
const MIN_GLIDE_MS = 250;
const MAX_GLIDE_MS = 7500;

export interface MotionFrame {
  position: RoutePoint;
  /** Metres along the route, when she is on it. */
  along: number | null;
}

type Glide =
  | { mode: 'road'; from: number; to: number; start: number; duration: number }
  | { mode: 'straight'; from: RoutePoint; to: RoutePoint; start: number; duration: number };

/**
 * One marker's movement between fixes. setTarget() with each real fix;
 * subscribers are called on every animation frame while it moves, and not
 * at all while it stands still.
 */
export class MarkerMotion {
  private line: PreparedLine | null = null;
  private frame: MotionFrame | null = null;
  private target: RoutePoint | null = null;
  private targetAt = 0;
  private hint = 0;
  private glide: Glide | null = null;
  private raf: number | null = null;
  private listeners = new Set<(f: MotionFrame | null) => void>();

  current(): MotionFrame | null {
    return this.frame;
  }

  subscribe(fn: (f: MotionFrame | null) => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  /** A new route (or none). Where she is drawn stays put; only her place along it is worked out again. */
  setLine(line: PreparedLine | null) {
    if (line === this.line) return;
    this.line = line;
    this.hint = 0;
    this.stop();
    if (!this.frame || !this.target) return this.emit();
    // Settle on the newest real fix against the new line.
    this.frame = this.place(this.target);
    this.emit();
  }

  setTarget(next: RoutePoint | null, now = performance.now()) {
    if (!next) {
      this.stop();
      this.target = null;
      this.frame = null;
      this.emit();
      return;
    }
    const first = !this.frame || !this.target;
    if (!first && metresBetween(this.target!, next) < JITTER_METRES) return;

    const gap = first ? 0 : now - this.targetAt;
    const duration = Math.max(MIN_GLIDE_MS, Math.min(MAX_GLIDE_MS, gap));
    const prevTarget = this.target;
    this.target = next;
    this.targetAt = now;

    if (first) {
      this.frame = this.place(next);
      this.emit();
      return;
    }

    const from = this.frame!;
    const line = this.line;
    const proj = line ? projectOnLine(line, next, this.hint) : null;
    if (line && proj && proj.dist <= ON_ROUTE_METRES && from.along != null) {
      this.hint = proj.seg;
      const slip = from.along - proj.along;
      // A small slide backwards along the road is the GPS wandering, not
      // her turning round: hold where she is.
      if (slip > 0 && slip < BACKSLIDE_METRES) return;
      if (Math.abs(proj.along - from.along) / (duration / 1000) > MAX_GLIDE_SPEED_MPS) return this.jump(next);
      this.run({ mode: 'road', from: from.along, to: proj.along, start: now, duration });
      return;
    }

    const hop = metresBetween(from.position, next);
    const fromSpeed = prevTarget ? metresBetween(prevTarget, next) / (duration / 1000) : Infinity;
    if (hop / (duration / 1000) > MAX_GLIDE_SPEED_MPS || fromSpeed > MAX_GLIDE_SPEED_MPS) return this.jump(next);
    if (proj && proj.dist <= ON_ROUTE_METRES) this.hint = proj.seg;
    this.run({ mode: 'straight', from: from.position, to: this.place(next).position, start: now, duration });
  }

  dispose() {
    this.stop();
    this.listeners.clear();
  }

  /** Where a fix is drawn: on the road when she is on it, exactly where it says otherwise. */
  private place(p: RoutePoint): MotionFrame {
    if (this.line) {
      const proj = projectOnLine(this.line, p, this.hint);
      if (proj.dist <= ON_ROUTE_METRES) {
        this.hint = proj.seg;
        return { position: proj.at, along: proj.along };
      }
    }
    return { position: p, along: null };
  }

  private jump(p: RoutePoint) {
    this.stop();
    this.frame = this.place(p);
    this.emit();
  }

  private run(glide: Glide) {
    this.glide = glide;
    if (this.raf == null) this.raf = requestAnimationFrame(this.tick);
  }

  private stop() {
    if (this.raf != null) cancelAnimationFrame(this.raf);
    this.raf = null;
    this.glide = null;
  }

  private tick = (now: number) => {
    this.raf = null;
    const g = this.glide;
    if (!g) return;
    // Linear, as a vehicle moves: an ease-out would make her look as if she
    // braked at every fix.
    const t = Math.max(0, Math.min(1, (now - g.start) / g.duration));
    if (g.mode === 'road' && this.line) {
      const along = g.from + (g.to - g.from) * t;
      this.frame = { position: pointAlong(this.line, along).point, along };
    } else if (g.mode === 'straight') {
      const position = { lat: g.from.lat + (g.to.lat - g.from.lat) * t, lng: g.from.lng + (g.to.lng - g.from.lng) * t };
      // Joining the route partway through a straight glide: her progress along it starts counting at the end.
      this.frame = t < 1 ? { position, along: null } : this.place(this.target ?? position);
    }
    this.emit();
    if (t < 1) this.raf = requestAnimationFrame(this.tick);
    else this.glide = null;
  };

  private emit() {
    this.listeners.forEach((fn) => fn(this.frame));
  }
}
