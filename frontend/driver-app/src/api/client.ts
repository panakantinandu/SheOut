import type { PushApi } from '@sheout/design-system';
import type {
  ApiErrorResponse,
  AuthSession,
  BookingCategory,
  BookingStatus,
  BookingSummary,
  PaymentHold,
  CancellationReason,
  ChatMessage,
  ChatThreadResponse,
  SupportContact,
  SupportTicket,
  SupportTicketCategory,
  SupportTicketMessage,
  SupportTicketStatus,
  SupportTicketThreadResponse,
  Rating,
  RatingTagCatalogue,
  AggregateRating,
  PagedResult,
  DriverOnlineStatus,
  DriverProfileSummary,
  OfferSummary,
  TripRoute,
  VehicleType,
  VerificationSummary,
  NotificationView,
  InboxPage,
  PushConfig,
  PaymentSummary,
  PayoutAccountView,
  PayoutOverview,
  PayoutRequestView,
  SavePayoutAccount,
} from './types';

// VITE_API_BASE_URL lets each deployment point at its own backend (Vercel
// env var for prod, .env.local for local dev override) - hardcoding
// localhost:8080 here would make the deployed app unusable, since that
// only resolves on the machine running the backend, not a visitor's browser.
//
// `||`, deliberately not `??`: this was set to an empty string in Vercel
// once, and `??` only falls back on null/undefined, so "" passed straight
// through. API_BASE became "", every call went to the app's own origin as
// a relative path, and the whole app 404'd against itself with nothing in
// the code looking wrong. An empty value means "not configured" here, so
// it must fall back like a missing one.
const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
/**
 * Turns a filter object into a query string, dropping anything unset.
 * An unset filter must be absent, not present-and-empty: the backend cannot
 * parse a blank enum, where an omitted parameter correctly means "do not
 * narrow". An array becomes a repeated parameter, which is how Spring binds
 * a Set.
 */
function buildQuery(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      if (value.length === 0) continue;
      for (const entry of value) search.append(key, String(entry));
    } else {
      search.append(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `?${query}` : '';
}

const TOKEN_STORAGE_KEY = 'sheout_driver_access_token';

/** Where the reason is left for the sign-in screen to read once. */
const SESSION_ENDED_KEY = 'sheout_session_ended';

export type SessionEndedReason = 'SIGNED_IN_ELSEWHERE' | 'ACCOUNT_BLOCKED' | 'ACCOUNT_DELETED' | 'SIGNED_OUT';

/** Read once from storage, then remembered for this page. */
let sessionEndedReason: SessionEndedReason | null | undefined;

/**
 * Why the last session ended, for the sign-in screen to explain.
 * <p>
 * Taken out of storage on the first call and kept in memory after that: React
 * renders a component's initial state twice in development, and a plain
 * read-and-delete lost the reason to the second render - the screen then had
 * nothing to say. Storage is still cleared, so a later reload does not repeat
 * an old message.
 */
export function takeSessionEndedReason(): SessionEndedReason | null {
  if (sessionEndedReason === undefined) {
    try {
      sessionEndedReason = sessionStorage.getItem(SESSION_ENDED_KEY) as SessionEndedReason | null;
      sessionStorage.removeItem(SESSION_ENDED_KEY);
    } catch {
      sessionEndedReason = null;
    }
  }
  return sessionEndedReason;
}

/**
 * The server has ended this session - she signed in elsewhere, an admin
 * blocked the account, she signed this device out from another one, or the
 * token simply is not accepted any more.
 * <p>
 * Handled here rather than screen by screen: it can land on any request, and
 * a screen that only knows "401" would leave her tapping a dead app. Any 401
 * while a token is stored means that token is finished - with a reason when
 * the server gave one, silently when it did not. The token goes, the reason is
 * kept for the sign-in screen, and the app restarts there.
 */
function onSessionEnded(body: ApiErrorResponse | undefined) {
  const detail = body?.details?.find((d) => d.startsWith('reason:'));
  const reason = (detail ? detail.split(':')[1].trim() : 'SIGNED_OUT') as SessionEndedReason;
  setStoredToken(null);
  try {
    sessionStorage.setItem(SESSION_ENDED_KEY, reason);
  } catch {
    // Private browsing: she still gets signed out, just without the reason.
  }
  if (!window.location.pathname.startsWith('/login')) {
    window.location.replace('/login');
  }
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly body: ApiErrorResponse | null
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function getStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

function setStoredToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_STORAGE_KEY, token);
    else localStorage.removeItem(TOKEN_STORAGE_KEY);
  } catch {
    // Private browsing / storage disabled - session just won't persist across reloads.
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
}

/**
 * The one place every HTTP call in this app goes through - screens never
 * call fetch() directly, matching the same "no scattered logic" principle
 * the backend prompts used for repositories/state machines.
 * <p>
 * A FormData body (document upload) skips JSON encoding and the
 * Content-Type header - the browser sets the multipart boundary itself,
 * setting Content-Type by hand here would omit that boundary and break
 * the upload.
 */
async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options;
  const headers: Record<string, string> = {};
  const isFormData = body instanceof FormData;
  if (body !== undefined && !isFormData) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = getStoredToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : isFormData ? (body as FormData) : JSON.stringify(body),
  });

  if (response.status === 204 || response.status === 202) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const errorBody = data as ApiErrorResponse | undefined;
    if (response.status === 401 && getStoredToken()) onSessionEnded(errorBody);
    throw new ApiError(errorBody?.message ?? `Request failed (${response.status})`, response.status, errorBody ?? null);
  }

  return data as T;
}

export const authApi = {
  requestOtp(phoneNumber: string): Promise<void> {
    return request('/api/v1/auth/otp/request', {
      method: 'POST',
      body: { phoneNumber, role: 'DRIVER' },
      auth: false,
    });
  },

  async verifyOtp(phoneNumber: string, code: string): Promise<AuthSession> {
    const session = await request<AuthSession>('/api/v1/auth/otp/verify', {
      method: 'POST',
      body: { phoneNumber, code, role: 'DRIVER' },
      auth: false,
    });
    setStoredToken(session.accessToken);
    return session;
  },

  /**
   * Ends the session on the server as well as on this device, so the token
   * cannot be used again by anything that kept a copy. Fire-and-forget: the
   * request carries the token, which the next line takes away, and signing
   * out never waits on the network.
   */
  logout(): void {
    void request('/api/v1/auth/logout', { method: 'POST' }).catch(() => undefined);
    setStoredToken(null);
  },
};

export const usersApi = {
  getMyProfile(): Promise<DriverProfileSummary> {
    return request('/api/v1/users/driver/me');
  },

  /**
   * PUT replaces the whole profile. 400 UNDER_MINIMUM_AGE /
   * INVALID_DATE_OF_BIRTH / INVALID_EMAIL / INVALID_REGISTRATION_NUMBER, 409
   * PROFILE_PHOTO_REQUIRED until a photo has been uploaded.
   */
  updateMyProfile(update: {
    name: string;
    vehicleType: VehicleType;
    vehicleRegistrationNumber: string;
    dateOfBirth: string;
    email?: string;
  }): Promise<DriverProfileSummary> {
    return request('/api/v1/users/driver/me', { method: 'PUT', body: update });
  },

  /**
   * Saves her PAN, for deducting TDS on payouts.
   * <p>
   * Its own call rather than a field on updateMyProfile: it is collected
   * with her documents, and a profile save that did not carry it would
   * otherwise wipe it. An empty string removes it. 400 INVALID_PAN when the
   * shape is wrong.
   */
  updateMyPan(panNumber: string): Promise<DriverProfileSummary> {
    return request('/api/v1/users/driver/me/pan', { method: 'PUT', body: { panNumber } });
  },

  /**
   * Uploads the photo a rider sees on her tracking screen. Required before
   * going online for the first time - the backend refuses ONLINE without
   * one, with its own machine code so this app can send her to the camera
   * rather than showing a refusal she cannot act on.
   */
  uploadMyPhoto(file: File): Promise<DriverProfileSummary> {
    const form = new FormData();
    form.append('file', file);
    return request('/api/v1/users/driver/me/photo', { method: 'POST', body: form });
  },

  /**
   * Going ONLINE carries where she is; going OFFLINE does not and must not.
   * <p>
   * SheOut operates in one city, and "offer me trips near me" cannot be
   * answered without a position - this used to succeed from anywhere on
   * earth, leaving a partner reading "Looking for ride requests nearby"
   * thousands of kilometres from the nearest possible rider. Refused as 409
   * OUTSIDE_SERVICE_AREA, or 400 LOCATION_REQUIRED when no fix was sent.
   * <p>
   * Stopping work asks for nothing. A partner must be able to go offline
   * anywhere, including with location switched off.
   */
  setOnlineStatus(
    status: DriverOnlineStatus,
    at?: { lat: number; lng: number }
  ): Promise<DriverProfileSummary> {
    return request('/api/v1/users/driver/me/status', {
      method: 'POST',
      body: { status, lat: at?.lat, lng: at?.lng },
    });
  },
};

export const notificationsApi = {
  inbox(page: number): Promise<InboxPage> {
    return request(`/api/v1/notifications/me${buildQuery({ page, pageSize: 20 })}`);
  },

  unreadCount(): Promise<number> {
    return request<{ unreadCount: number }>('/api/v1/notifications/me/unread-count').then((r) => r.unreadCount);
  },

  markRead(notificationId: string): Promise<NotificationView> {
    return request(`/api/v1/notifications/${notificationId}/read`, { method: 'POST' });
  },

  markAllRead(): Promise<void> {
    return request('/api/v1/notifications/me/read-all', { method: 'POST' });
  },
};

/** Where this device's FCM token is remembered, so sign-out can unregister it. */
export const PUSH_TOKEN_KEY = 'sheout_driver_push_token';

/** What the shared push code needs - see @sheout/design-system's lib/push. */
export const pushApi: PushApi = {
  getConfig(): Promise<PushConfig> {
    return request('/api/v1/notifications/push-config', { auth: false });
  },
  registerDevice(token: string): Promise<void> {
    return request('/api/v1/notifications/devices', { method: 'POST', body: { token } });
  },
  unregisterDevice(token: string): Promise<void> {
    return request('/api/v1/notifications/devices/unregister', { method: 'POST', body: { token } });
  },
};

export const verificationApi = {
  getMyStatus(): Promise<VerificationSummary> {
    return request('/api/v1/driver-verification/me');
  },

  /**
   * Both documents in one call, because they are evidence for one decision:
   * the ID establishes who she is, the registration certificate lets an
   * operator check the number she typed against the vehicle she owns.
   * Submitting half would put her in the queue as a row nobody can action.
   */
  uploadDocuments(file: File, rcFile: File): Promise<VerificationSummary> {
    const form = new FormData();
    form.append('file', file);
    form.append('rcFile', rcFile);
    return request('/api/v1/driver-verification/documents', { method: 'POST', body: form });
  },
};

/** What a partner is told about her rider - see the backend's assignedRider. */
export interface AssignedRider {
  firstName: string | null;
  photoUrl: string | null;
  averageStars: number | null;
  totalRatings: number;
}

export const dispatchApi = {
  /** 404 until she has accepted the trip. */
  getAssignedRider(bookingId: string): Promise<AssignedRider> {
    return request(`/api/v1/dispatch/bookings/${bookingId}/rider`);
  },

  recordLocation(lat: number, lng: number): Promise<void> {
    return request('/api/v1/dispatch/location', { method: 'POST', body: { lat, lng } });
  },

  /** null = 204 No Content (no active offer right now) - see OfferSummary comment. */
  getMyOffer(): Promise<OfferSummary | null> {
    return request<OfferSummary | undefined>('/api/v1/dispatch/offers/me').then((v) => v ?? null);
  },

  acceptOffer(bookingId: string): Promise<void> {
    return request(`/api/v1/dispatch/offers/${bookingId}/accept`, { method: 'POST' });
  },

  declineOffer(bookingId: string): Promise<void> {
    return request(`/api/v1/dispatch/offers/${bookingId}/decline`, { method: 'POST' });
  },
};

/**
 * The signed-in account's own preferences and waitlist places. Scoped by the
 * token - there is no account id to pass.
 */
export const preferencesApi = {
  get(): Promise<{ language: string | null }> {
    return request('/api/v1/users/me/preferences');
  },
  save(language: string): Promise<{ language: string }> {
    return request('/api/v1/users/me/preferences/language', { method: 'PUT', body: { language } });
  },
};

export const bookingApi = {
  /**
   * The ended trip still waiting for the rider's payment that is keeping new
   * offers away, or null. Lifts on its own at holdUntil.
   */
  async getPaymentHold(): Promise<PaymentHold | null> {
    return (await request<PaymentHold | undefined>('/api/v1/bookings/me/payment-hold')) ?? null;
  },

  /**
   * The partner's own trips, paged and filtered. Same endpoint the rider
   * app calls - it is scoped by the token, so a DRIVER token returns the
   * trips they drove.
   */
  search(params: {
    page?: number;
    pageSize?: number;
    status?: BookingStatus[];
    from?: string;
    to?: string;
    category?: BookingCategory[];
    q?: string;
  }): Promise<PagedResult<BookingSummary>> {
    return request(`/api/v1/bookings/me/search${buildQuery(params)}`);
  },

  listMine(): Promise<BookingSummary[]> {
    return request('/api/v1/bookings/me');
  },

  getById(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}`);
  },

  /** Confirms a dispatch-won assignment: MATCHED -> ACCEPTED. */
  accept(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}/accept`, { method: 'POST' });
  },

  /**
   * ACCEPTED -> IN_PROGRESS, and only with the code the rider read out.
   * <p>
   * The code is required. This used to post nothing at all, which is
   * precisely the gap it closes: a partner could start - and then complete -
   * a trip with nobody in the vehicle, and the rider was charged for it.
   * <p>
   * Fails as 400 INVALID_PICKUP_CODE for a wrong code and 409
   * PICKUP_VERIFICATION_LOCKED once the attempt limit is spent. The Trip
   * screen tells those apart: one keeps the keypad open, the other does not.
   */
  start(bookingId: string, pickupCode: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}/start`, {
      method: 'POST',
      body: { pickupCode },
    });
  },

  /**
   * The road from where she is now to wherever this booking says she is
   * going next - the pickup during ACCEPTED, the drop during IN_PROGRESS.
   * <p>
   * The destination is the server's decision, not a parameter, so her map
   * and the booking can never disagree about which leg is happening.
   * <p>
   * Called about twice per trip, not on every position update: it routes
   * through a volunteer-run OSRM instance, and the in-app line is for
   * orientation while the real turn-by-turn happens in Google Maps.
   */
  getRoute(bookingId: string, from: { lat: number; lng: number }): Promise<TripRoute> {
    return request(`/api/v1/bookings/${bookingId}/route?fromLat=${from.lat}&fromLng=${from.lng}`);
  },

  complete(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}/complete`, { method: 'POST' });
  },

  /**
   * A reason is required, and the backend rejects a cancel without one.
   * <p>
   * This used to post an empty body. It stopped being allowed to when
   * cancellations became something an account is answerable for: a
   * cancellation with no reason cannot be told apart from any other, which
   * makes every number built on it meaningless.
   */
  cancel(bookingId: string, reason: CancellationReason, note?: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}/cancel`, {
      method: 'POST',
      body: { reason, note },
    });
  },
};

/**
 * Booking-scoped chat with the rider.
 * <p>
 * There is no endpoint anywhere that hands a partner a rider's phone
 * number, and there never was. This is the whole channel between them.
 */
export const chatApi = {
  /** The thread, whether it can still be written to, and the support number. */
  getThread(bookingId: string): Promise<ChatThreadResponse> {
    return request(`/api/v1/bookings/${bookingId}/chat`);
  },

  /**
   * 409 CHAT_CLOSED once the trip has ended, 400 CONTACT_DETAILS_NOT_ALLOWED
   * for a message carrying something phone-number shaped. Both are refusals
   * the screen should show, not failures to retry.
   */
  send(bookingId: string, body: string): Promise<ChatMessage> {
    return request(`/api/v1/bookings/${bookingId}/chat`, { method: 'POST', body: { body } });
  },
};

/**
 * The number for reaching a person at SheOut.
 * <p>
 * Unauthenticated, deliberately: somebody who cannot sign in is exactly the
 * person most likely to need it, so the way to reach a human must not
 * depend on a working session.
 */
/**
 * The account holder's data rights.
 * <p>
 * Download goes through fetch rather than a plain link because the endpoint
 * needs the bearer token, which a link cannot carry; the file is then handed
 * to the browser to save.
 */
export const privacyApi = {
  async downloadMyData(): Promise<void> {
    const token = getStoredToken();
    const response = await fetch(`${API_BASE}/api/v1/privacy/export`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) {
      const body = (await response.json().catch(() => null)) as ApiErrorResponse | null;
      throw new ApiError(body?.message ?? `Request failed (${response.status})`, response.status, body);
    }
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const filename = /filename="([^"]+)"/.exec(disposition)?.[1] ?? 'sheout-my-data.json';
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    // Revoked after the click has been handled, not before.
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  },

  /**
   * Deletes the account. 409 ACTIVE_TRIP while a trip is under way. After
   * success the stored token is useless - the server no longer accepts it -
   * so the caller signs out.
   */
  deleteAccount(): Promise<{ requestedAt: string; completedAt: string }> {
    return request('/api/v1/privacy/delete-account', { method: 'POST', body: { confirmation: 'DELETE' } });
  },
};

/**
 * Editable app copy - see the content module. Unauthenticated: it is the
 * text on public screens. Screens read it through useContentSection, which
 * caches it, rather than calling this directly.
 */
export const contentApi = {
  getSection(prefix: string): Promise<Record<string, string>> {
    return request(`/api/v1/content${buildQuery({ prefix })}`, { auth: false });
  },
};

export const supportApi = {
  getContact(): Promise<SupportContact> {
    return request('/api/v1/support/contact', { auth: false });
  },

  /**
   * Raises a ticket. Priority is not sent - the server sets it from the
   * category. A linked trip must be one of the caller's own; anything else
   * is a 404, the same answer as a trip that does not exist.
   */
  raiseTicket(input: {
    category: SupportTicketCategory;
    subject: string;
    description: string;
    linkedBookingId?: string;
  }): Promise<SupportTicket> {
    return request('/api/v1/support/tickets', { method: 'POST', body: input });
  },

  /** The caller's own tickets, newest activity first, filtered like the other history lists. */
  myTickets(params: {
    page?: number;
    pageSize?: number;
    status?: SupportTicketStatus[];
    category?: SupportTicketCategory[];
    from?: string;
    to?: string;
  }): Promise<PagedResult<SupportTicket>> {
    return request(`/api/v1/support/tickets/me${buildQuery(params)}`);
  },

  getTicket(ticketId: string): Promise<SupportTicketThreadResponse> {
    return request(`/api/v1/support/tickets/${ticketId}`);
  },

  /** 409 TICKET_CLOSED once the ticket is closed - a refusal to show, not a failure to retry. */
  replyToTicket(ticketId: string, message: string): Promise<SupportTicketMessage> {
    return request(`/api/v1/support/tickets/${ticketId}/messages`, { method: 'POST', body: { message } });
  },
};

/**
 * Ratings. Who the caller is always comes from the token - there is no call
 * here that lets this app say whose rating it is or whom it is about.
 */
export const ratingsApi = {
  /** Trips the caller can still rate, soonest to close first. The prompt reads from this. */
  pending(): Promise<Rating[]> {
    return request('/api/v1/ratings/pending');
  },

  /**
   * Slots for several bookings at once, so a history page marks its rows in
   * one request rather than one per row. Bookings with nothing to rate are
   * simply absent from the result.
   */
  forBookings(bookingIds: string[]): Promise<Rating[]> {
    if (bookingIds.length === 0) return Promise.resolve([]);
    return request(`/api/v1/ratings/bookings${buildQuery({ bookingId: bookingIds })}`);
  },

  /** The caller's own slot for one booking. 404s when there is nothing to rate. */
  forBooking(bookingId: string): Promise<Rating> {
    return request(`/api/v1/ratings/bookings/${bookingId}`);
  },

  /**
   * The quick reasons this caller may be offered, by star rating.
   * <p>
   * Served rather than built in, so the words tapped here, the words the
   * other app tapped and the words an operator reads are one list. A failure
   * here shows no tags and changes nothing else - rating is a tap on a star
   * and must not depend on this having succeeded.
   */
  tags(): Promise<RatingTagCatalogue> {
    return request('/api/v1/ratings/tags');
  },

  /**
   * 409 ALREADY_RATED on a second attempt, 409 RATING_WINDOW_CLOSED once the
   * window has passed, 400 INVALID_RATING_TAG for a tag that does not go with
   * these stars - which the dialog prevents by clearing them when the stars change.
   */
  submit(bookingId: string, stars: number, comment?: string, tags?: string[]): Promise<Rating> {
    return request(`/api/v1/ratings/bookings/${bookingId}`, { method: 'POST', body: { stars, comment, tags } });
  },

  /** Somebody else's public score. Only the average and the count, never who gave what. */
  forAccount(accountId: string): Promise<AggregateRating> {
    return request(`/api/v1/ratings/accounts/${accountId}`);
  },

  /** The caller's own score, for their dashboard. */
  mine(): Promise<AggregateRating> {
    return request('/api/v1/ratings/me');
  },
};

export const paymentsApi = {
  /** 404s until the payment row exists, a moment after the trip completes. */
  getForBooking(bookingId: string): Promise<PaymentSummary> {
    return request(`/api/v1/payments/bookings/${bookingId}`);
  },
  // No cash confirmation any more: every fare is paid by the rider in her
  // app and lands in the partner's wallet. See the backend's PaymentController.
};

export const payoutsApi = {
  overview(): Promise<PayoutOverview> {
    return request('/api/v1/payouts/me');
  },

  /** 400 INVALID_DETAILS for a malformed IFSC, account number or UPI ID, or a half-filled bank account. */
  saveAccount(account: SavePayoutAccount): Promise<PayoutAccountView> {
    return request('/api/v1/payouts/me/account', { method: 'PUT', body: account });
  },

  /** 409 NO_PAYOUT_DETAILS before details are saved, 409 INSUFFICIENT_BALANCE above the available balance. */
  requestPayout(amount: number): Promise<PayoutRequestView> {
    return request('/api/v1/payouts/me/requests', { method: 'POST', body: { amount } });
  },
};
