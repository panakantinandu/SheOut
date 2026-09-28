/**
 * Recognising a deliberate discreet-SOS gesture in accelerometer readings.
 * <p>
 * Pure - no browser APIs - so it can be tested with made-up readings. Fed
 * one reading at a time (linear acceleration, gravity removed, in m/s²),
 * it answers 'SHAKE', 'BACK_TAP' or null.
 * <p>
 * Two patterns, each chosen because it is hard to produce by accident:
 * <ul>
 *   <li>SHAKE: at least six strong jolts (over 18 m/s², nearly two g on top
 *       of gravity) that change direction each time, within 1.5 seconds.
 *       A phone bouncing in a bag or a bumpy road gives jolts, but not six
 *       hard back-and-forth ones in a row.</li>
 *   <li>BACK_TAP: two sharp knocks on the back of the phone, 120-450 ms
 *       apart, with the phone otherwise still before and after. A single
 *       jolt - the phone set down, a pothole - is one knock, never two with
 *       stillness around them.</li>
 * </ul>
 * Either one only starts a 3-second countdown the user can cancel; it never
 * sends an alert by itself.
 */

export type Gesture = 'SHAKE' | 'BACK_TAP';

export interface MotionSample {
  /** Milliseconds, any monotonic clock. */
  t: number;
  x: number;
  y: number;
  z: number;
}

const SHAKE_THRESHOLD = 18;
const SHAKE_PEAKS = 6;
const SHAKE_WINDOW_MS = 1500;
const SHAKE_MIN_GAP_MS = 70;

const KNOCK_THRESHOLD = 12;
const KNOCK_MIN_GAP_MS = 120;
const KNOCK_MAX_GAP_MS = 450;
const KNOCK_MAX_WIDTH_MS = 80;
const STILL_LEVEL = 3;
const STILL_BEFORE_MS = 400;
const STILL_AFTER_MS = 300;

const COOLDOWN_MS = 4000;

interface Peak {
  t: number;
  axis: 'x' | 'y' | 'z';
  sign: number;
}

export class MotionPatternDetector {
  private samples: MotionSample[] = [];
  private shakePeaks: Peak[] = [];
  private lastDetection = -Infinity;
  /** A second knock we are holding until the stillness after it is confirmed. */
  private pendingKnocks: { first: number; second: number } | null = null;

  feed(s: MotionSample): Gesture | null {
    this.samples.push(s);
    // Keep only what the patterns can look back over.
    while (this.samples.length > 0 && s.t - this.samples[0].t > 2000) this.samples.shift();
    if (s.t - this.lastDetection < COOLDOWN_MS) return null;

    const shake = this.checkShake(s);
    if (shake) return this.detected(s.t, 'SHAKE');
    const knock = this.checkBackTap(s);
    if (knock) return this.detected(s.t, 'BACK_TAP');
    return null;
  }

  private detected(t: number, gesture: Gesture): Gesture {
    this.lastDetection = t;
    this.shakePeaks = [];
    this.pendingKnocks = null;
    return gesture;
  }

  private checkShake(s: MotionSample): boolean {
    const axis = dominantAxis(s);
    const value = s[axis];
    if (Math.abs(value) >= SHAKE_THRESHOLD) {
      const sign = Math.sign(value);
      const last = this.shakePeaks[this.shakePeaks.length - 1];
      if (!last || (s.t - last.t >= SHAKE_MIN_GAP_MS && (last.axis !== axis || last.sign !== sign))) {
        this.shakePeaks.push({ t: s.t, axis, sign });
      }
    }
    this.shakePeaks = this.shakePeaks.filter((p) => s.t - p.t <= SHAKE_WINDOW_MS);
    return this.shakePeaks.length >= SHAKE_PEAKS;
  }

  private checkBackTap(s: MotionSample): boolean {
    // A pending double knock is confirmed once the phone has stayed still after it.
    if (this.pendingKnocks) {
      if (magnitude(s) > STILL_LEVEL && s.t - this.pendingKnocks.second > KNOCK_MAX_WIDTH_MS) {
        this.pendingKnocks = null; // it kept moving: not a deliberate knock
      } else if (s.t - this.pendingKnocks.second >= STILL_AFTER_MS) {
        this.pendingKnocks = null;
        return true;
      }
      return false;
    }
    const knocks = this.knockTimes();
    if (knocks.length < 2) return false;
    const second = knocks[knocks.length - 1];
    const first = knocks[knocks.length - 2];
    const gap = second - first;
    if (gap < KNOCK_MIN_GAP_MS || gap > KNOCK_MAX_GAP_MS || s.t - second > 40) return false;
    if (knocks.length > 2 && first - knocks[knocks.length - 3] < STILL_BEFORE_MS) return false;
    // Still before the first knock (the samples that exist), and between the two.
    const before = this.samples.filter((x) => x.t < first - KNOCK_MAX_WIDTH_MS && x.t >= first - STILL_BEFORE_MS);
    const between = this.samples.filter((x) => x.t > first + KNOCK_MAX_WIDTH_MS && x.t < second - KNOCK_MAX_WIDTH_MS);
    if (before.length === 0 || before.some((x) => magnitude(x) > STILL_LEVEL)) return false;
    if (between.some((x) => magnitude(x) > STILL_LEVEL)) return false;
    this.pendingKnocks = { first, second };
    return false;
  }

  /** Start times of the sharp spikes in the window: a spike is a run of readings over the threshold, no wider than a knock. */
  private knockTimes(): number[] {
    const times: number[] = [];
    let runStart: number | null = null;
    let runEnd = 0;
    for (const x of this.samples) {
      if (Math.abs(x.z) >= KNOCK_THRESHOLD || magnitude(x) >= KNOCK_THRESHOLD) {
        if (runStart === null) runStart = x.t;
        runEnd = x.t;
      } else if (runStart !== null) {
        if (runEnd - runStart <= KNOCK_MAX_WIDTH_MS) times.push(runStart);
        runStart = null;
      }
    }
    if (runStart !== null && runEnd - runStart <= KNOCK_MAX_WIDTH_MS) times.push(runStart);
    return times;
  }
}

function magnitude(s: MotionSample): number {
  return Math.sqrt(s.x * s.x + s.y * s.y + s.z * s.z);
}

function dominantAxis(s: MotionSample): 'x' | 'y' | 'z' {
  const ax = Math.abs(s.x);
  const ay = Math.abs(s.y);
  const az = Math.abs(s.z);
  return ax >= ay && ax >= az ? 'x' : ay >= az ? 'y' : 'z';
}
