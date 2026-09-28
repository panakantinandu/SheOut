import { notificationsApi, ApiError } from '../api/client';
import type { SosResponse } from '../api/types';
import { openSmsComposer } from './emergency';

/**
 * Getting an SOS out when the connection is bad.
 * <p>
 * Three things, at once rather than one after another:
 * <ol>
 *   <li>DATA - the alert goes to SheOut, which texts her contacts and alerts
 *       operations. Kept trying for as long as it takes.</li>
 *   <li>SMS FALLBACK - if SheOut has not answered within FALLBACK_AFTER_MS,
 *       her phone's own SMS app is opened with her contacts and location
 *       filled in, while the data attempt carries on. With no signal at all
 *       it is opened straight away. A web app cannot send a text by itself:
 *       she still presses Send, and the screen says so.</li>
 *   <li>DELAYED QUEUE - every alert is written to this phone first, and is
 *       only removed once SheOut has confirmed it. With no signal it stays,
 *       and is sent the moment a connection returns (see flushSosQueue),
 *       without her doing anything - marked DELAYED_QUEUE with the time she
 *       actually raised it.</li>
 * </ol>
 * The phone's own id for the alert (clientAlertId) makes all of this safe to
 * repeat: SheOut treats the same id as the same alert and never texts her
 * contacts twice. It also records whether the SMS fallback was opened, so
 * the console shows how each alert really went out.
 * <p>
 * Why 4 seconds and not 5: a browser lets a page open another app (here the
 * SMS app) without a fresh tap only for about five seconds after the tap
 * that started it. Opening at four keeps it inside that window when she
 * pressed SOS. After a discreet gesture there was no tap, so the phone may
 * refuse - the SOS screen always shows a "text my contacts" button as well.
 */

export const FALLBACK_AFTER_MS = 4000;
const QUEUE_KEY = 'sheout_sos_queue';
const CONTACTS_KEY = 'sheout_sos_contacts';
const POSITION_KEY = 'sheout_last_position';
/** An alert older than this is not sent from the queue: it would mislead more than help. */
const QUEUE_MAX_AGE_MS = 12 * 60 * 60 * 1000;

export type TriggerSource = 'BUTTON' | 'SHAKE' | 'BACK_TAP' | 'SHORTCUT';

export interface QueuedSos {
  clientAlertId: string;
  lat: number;
  lng: number;
  bookingId?: string;
  triggerSource: TriggerSource;
  triggeredAt: string;
  smsFallbackOpened: boolean;
}

export interface CachedContacts {
  names: string[];
  numbers: string[];
  myName?: string;
}

export type SosDelivery =
  | { kind: 'delivered'; response: SosResponse; smsOpened: boolean }
  | { kind: 'queued'; smsOpened: boolean }
  | { kind: 'failed'; message: string; smsOpened: boolean };

// ---------------------------------------------------------------- storage

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

function write(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage blocked: the live attempt still runs; only the queue is lost.
  }
}

export function pendingSos(): QueuedSos[] {
  return read<QueuedSos[]>(QUEUE_KEY, []).filter((a) => Date.now() - new Date(a.triggeredAt).getTime() < QUEUE_MAX_AGE_MS);
}

function saveQueue(items: QueuedSos[]): void {
  write(QUEUE_KEY, items);
}

function upsert(item: QueuedSos): void {
  saveQueue([...pendingSos().filter((a) => a.clientAlertId !== item.clientAlertId), item]);
}

function remove(clientAlertId: string): void {
  saveQueue(pendingSos().filter((a) => a.clientAlertId !== clientAlertId));
}

/** Her contacts, kept on the phone so the SMS fallback works with no connection at all. */
export function cacheContacts(contacts: CachedContacts): void {
  write(CONTACTS_KEY, contacts);
}

export function cachedContacts(): CachedContacts {
  return read<CachedContacts>(CONTACTS_KEY, { names: [], numbers: [] });
}

/** Her last known position - used when a fresh fix cannot be had in an emergency. */
export function cachePosition(lat: number, lng: number): void {
  write(POSITION_KEY, { lat, lng, at: Date.now() });
}

export function cachedPosition(): { lat: number; lng: number; at: number } | null {
  return read<{ lat: number; lng: number; at: number } | null>(POSITION_KEY, null);
}

function newId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-8xxx-xxxxxxxxxxxx'.replace(/x/g, () => Math.floor(Math.random() * 16).toString(16));
}

// ---------------------------------------------------------------- sending

function post(item: QueuedSos, channel: 'DATA' | 'DELAYED_QUEUE'): Promise<SosResponse> {
  return notificationsApi.triggerSos({
    lat: item.lat,
    lng: item.lng,
    bookingId: item.bookingId,
    triggerSource: item.triggerSource,
    deliveryChannel: channel,
    triggeredAt: item.triggeredAt,
    clientAlertId: item.clientAlertId,
    smsFallbackOpened: item.smsFallbackOpened,
  });
}

/**
 * Raises an SOS by every route at once. smsBody is the text her contacts
 * get from her own phone if it comes to that.
 */
export async function sendSos(
  alert: { lat: number; lng: number; bookingId?: string; triggerSource: TriggerSource },
  smsBody: string,
  onSmsOpened?: () => void
): Promise<SosDelivery> {
  const item: QueuedSos = {
    ...alert,
    clientAlertId: newId(),
    triggeredAt: new Date().toISOString(),
    smsFallbackOpened: false,
  };
  // On the phone before anything else: if the app is closed a second from
  // now, the alert still goes when it next can.
  upsert(item);
  const numbers = cachedContacts().numbers;

  const openSms = () => {
    if (item.smsFallbackOpened || numbers.length === 0) return;
    item.smsFallbackOpened = true;
    upsert(item);
    try {
      openSmsComposer(numbers, smsBody);
    } catch {
      // The screen's own button is there too.
    }
    onSmsOpened?.();
  };

  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    openSms();
    return { kind: 'queued', smsOpened: item.smsFallbackOpened };
  }

  const timer = window.setTimeout(openSms, FALLBACK_AFTER_MS);
  try {
    const response = await post(item, 'DATA');
    window.clearTimeout(timer);
    remove(item.clientAlertId);
    return { kind: 'delivered', response, smsOpened: item.smsFallbackOpened };
  } catch (err) {
    window.clearTimeout(timer);
    if (err instanceof ApiError && err.status >= 400 && err.status < 500 && err.status !== 408 && err.status !== 429) {
      // SheOut answered and refused it (signed out, invalid) - retrying cannot help.
      remove(item.clientAlertId);
      openSms();
      return { kind: 'failed', message: err.message, smsOpened: item.smsFallbackOpened };
    }
    // No connection, a timeout, or SheOut down: it stays queued and goes when it can.
    openSms();
    return { kind: 'queued', smsOpened: item.smsFallbackOpened };
  }
}

/**
 * Sends whatever is waiting on the phone. Called when the connection comes
 * back, when the app opens, and every half minute while anything waits.
 * The same alert sent twice is recognised by SheOut, so a race between two
 * of these callers is harmless.
 */
let flushing = false;
export async function flushSosQueue(): Promise<number> {
  if (flushing) return 0;
  flushing = true;
  let sent = 0;
  try {
    for (const item of pendingSos()) {
      try {
        await post(item, 'DELAYED_QUEUE');
        remove(item.clientAlertId);
        sent++;
      } catch (err) {
        if (err instanceof ApiError && err.status >= 400 && err.status < 500 && err.status !== 401 && err.status !== 408 && err.status !== 429) {
          remove(item.clientAlertId);
        }
        // Otherwise: still no connection (or signed out) - try again later.
      }
    }
  } finally {
    flushing = false;
  }
  // Also drops anything past the age limit.
  saveQueue(pendingSos());
  return sent;
}
