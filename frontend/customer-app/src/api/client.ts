import type { PushApi } from '@sheout/design-system';
import type {
  ApiErrorResponse,
  AuthSession,
  BookingCategory,
  BookingStatus,
  BookingSummary,
  BookingType,
  CancellationReason,
  ChatMessage,
  ChatThreadResponse,
  AssignedDriver,
  SearchConfig,
  SupportContact,
  SupportTicket,
  SupportTicketCategory,
  SupportTicketMessage,
  SupportTicketStatus,
  SupportTicketThreadResponse,
  Rating,
  AggregateRating,
  CustomerProfileSummary,
  DriverLocation,
  PickupCodeResponse,
  EmergencyContact,
  FareQuote,
  GeoAddress,
  NotificationView,
  PagedResult,
  CheckoutDetails,
  CheckoutResult,
  InboxPage,
  PushConfig,
  PaymentStatus,
  PaymentSummary,
  SosResponse,
  VerificationSummary,
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
const TOKEN_STORAGE_KEY = 'sheout_access_token';

/**
 * Turns a filter object into a query string, dropping anything unset.
 * <p>
 * An unset filter must be absent, not present-and-empty: the backend reads
 * `status=` as a blank value and fails to parse it into an enum, where an
 * omitted `status` correctly means "do not narrow". An array becomes a
 * repeated parameter, which is how Spring binds a Set.
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
    throw new ApiError(errorBody?.message ?? `Request failed (${response.status})`, response.status, errorBody ?? null);
  }

  return data as T;
}

export const authApi = {
  requestOtp(phoneNumber: string): Promise<void> {
    return request('/api/v1/auth/otp/request', {
      method: 'POST',
      body: { phoneNumber, role: 'CUSTOMER' },
      auth: false,
    });
  },

  async verifyOtp(phoneNumber: string, code: string): Promise<AuthSession> {
    const session = await request<AuthSession>('/api/v1/auth/otp/verify', {
      method: 'POST',
      body: { phoneNumber, code, role: 'CUSTOMER' },
      auth: false,
    });
    setStoredToken(session.accessToken);
    return session;
  },

  /** googleAccessToken is the OAuth2 access token from Google Identity Services (see lib/googleAuth.ts) - the backend verifies it against Google's own endpoints, this never inspects it. */
  async googleSignIn(googleAccessToken: string): Promise<AuthSession> {
    const session = await request<AuthSession>('/api/v1/auth/google/verify', {
      method: 'POST',
      body: { accessToken: googleAccessToken, role: 'CUSTOMER' },
      auth: false,
    });
    setStoredToken(session.accessToken);
    return session;
  },

  logout(): void {
    setStoredToken(null);
  },
};

export const usersApi = {
  getMyProfile(): Promise<CustomerProfileSummary> {
    return request('/api/v1/users/customer/me');
  },

  /**
   * PUT replaces the whole profile, so callers pass every field back. 400
   * UNDER_MINIMUM_AGE / INVALID_DATE_OF_BIRTH / INVALID_EMAIL, 409
   * PROFILE_PHOTO_REQUIRED until a photo has been uploaded.
   */
  updateMyProfile(update: {
    name: string;
    homeAddress?: string;
    workAddress?: string;
    dateOfBirth: string;
    email?: string;
  }): Promise<CustomerProfileSummary> {
    return request('/api/v1/users/customer/me', { method: 'PUT', body: update });
  },

  uploadMyPhoto(file: File): Promise<CustomerProfileSummary> {
    const form = new FormData();
    form.append('file', file);
    return request('/api/v1/users/customer/me/photo', { method: 'POST', body: form });
  },

  getMyEmergencyContacts(): Promise<EmergencyContact[]> {
    return request('/api/v1/users/customer/me/emergency-contacts');
  },

  addEmergencyContact(contact: { name: string; phoneNumber: string; relationship: string }): Promise<EmergencyContact> {
    return request('/api/v1/users/customer/me/emergency-contacts', { method: 'POST', body: contact });
  },

  removeEmergencyContact(contactId: string): Promise<void> {
    return request(`/api/v1/users/customer/me/emergency-contacts/${contactId}`, { method: 'DELETE' });
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

  triggerSos(input: { lat: number; lng: number; bookingId?: string }): Promise<SosResponse> {
    return request('/api/v1/notifications/sos', { method: 'POST', body: input });
  },
};

/** Where this device's FCM token is remembered, so sign-out can unregister it. */
export const PUSH_TOKEN_KEY = 'sheout_push_token';

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

export const paymentsApi = {
  /** 404s until a payment row exists, which only happens once a trip completes. */
  getForBooking(bookingId: string): Promise<PaymentSummary> {
    return request(`/api/v1/payments/bookings/${bookingId}`);
  },

  /** Creates the Razorpay order if the trip has none yet. 409 once the trip is already paid. */
  getCheckout(bookingId: string): Promise<CheckoutDetails> {
    return request(`/api/v1/payments/bookings/${bookingId}/checkout`);
  },

  /**
   * Hands Checkout's success result to the server, which checks the signature
   * and confirms the capture with Razorpay itself before recording anything.
   * Checkout saying "paid" is not trusted on its own.
   */
  verifyCheckout(bookingId: string, result: CheckoutResult): Promise<PaymentSummary> {
    return request(`/api/v1/payments/bookings/${bookingId}/checkout/verify`, { method: 'POST', body: result });
  },

  /**
   * The caller's own payments, paged and filtered by date, status and
   * amount range.
   * <p>
   * Replaces what Payment History used to do: fetch every booking, then one
   * payment request per booking, then throw away the ones with no payment.
   * That was an N+1 from the browser that grew with the customer's whole
   * history and could not be paged or filtered at all.
   * <p>
   * No text search, deliberately - a payment has nothing worth typing at.
   */
  search(params: {
    page?: number;
    pageSize?: number;
    status?: PaymentStatus;
    from?: string;
    to?: string;
    minAmount?: number;
    maxAmount?: number;
  }): Promise<PagedResult<PaymentSummary>> {
    return request(`/api/v1/payments/me/search${buildQuery(params)}`);
  },
};

export const dispatchApi = {
  /**
   * 404s while no driver is assigned or none has reported a position yet -
   * both normal states the tracking screen polls through, not errors.
   */
  /**
   * The total search budget, so the waiting screen's own fallback sits just
   * behind the server's deadline instead of at a number guessed in this app.
   * Unauthenticated - see the backend endpoint for why.
   */
  getSearchConfig(): Promise<SearchConfig> {
    return request('/api/v1/dispatch/search-config', { auth: false });
  },

  /**
   * Who is coming, once she has actually accepted.
   * <p>
   * 404s during REQUESTED and MATCHED, and that is the server enforcing it,
   * not this app choosing not to ask. A partner's name, face, vehicle and
   * registration number are never released for a booking she has only been
   * assigned and might never confirm.
   */
  getAssignedDriver(bookingId: string): Promise<AssignedDriver> {
    return request(`/api/v1/dispatch/bookings/${bookingId}/driver`);
  },

  getDriverLocation(bookingId: string): Promise<DriverLocation> {
    return request(`/api/v1/dispatch/bookings/${bookingId}/driver-location`);
  },
};

export const verificationApi = {
  getMyStatus(): Promise<VerificationSummary> {
    return request('/api/v1/driver-verification/me');
  },

  /**
   * Submits an ID document for review.
   * <p>
   * The path says driver-verification, but the endpoint is not
   * driver-specific: it authenticates the caller and works purely from
   * their account id and verification record, with no reference to a
   * vehicle or driver profile. Customers need gender verification before
   * they can book, so they submit through the same endpoint rather than a
   * parallel one built to do the same thing.
   * <p>
   * Sent as multipart, so this bypasses request(): that helper always sets
   * a JSON content type, and a multipart body needs the browser to set its
   * own boundary.
   */
  async submitDocument(file: File): Promise<VerificationSummary> {
    const form = new FormData();
    form.append('file', file);
    const token = getStoredToken();
    const response = await fetch(`${API_BASE}/api/v1/driver-verification/documents`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : undefined;
    if (!response.ok) {
      const errorBody = data as ApiErrorResponse | undefined;
      throw new ApiError(errorBody?.message ?? `Upload failed (${response.status})`, response.status, errorBody ?? null);
    }
    return data as VerificationSummary;
  },
};

export const bookingApi = {
  create(input: { type: BookingType; category: BookingCategory; pickup: GeoAddress; drop: GeoAddress }): Promise<BookingSummary> {
    return request('/api/v1/bookings', { method: 'POST', body: input });
  },

  /** Prices a trip without creating one - see the backend's quote endpoint. */
  quote(input: { type: BookingType; category: BookingCategory; pickup: GeoAddress; drop: GeoAddress }): Promise<FareQuote> {
    return request('/api/v1/bookings/quote', { method: 'POST', body: input });
  },

  getById(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}`);
  },

  /**
   * The four digits she reads out to her partner at the kerb.
   * <p>
   * Its own call rather than a field on the booking, and that is the whole
   * design: GET /bookings/{id} is served to both participants, so a field
   * there would hand the partner the answer to the question she is being
   * asked, and the check would verify nothing.
   * <p>
   * 404s whenever there is no code to give - not her booking, or a booking
   * not in a state that has one. That is the normal case before ACCEPTED
   * and after the trip starts, not an error to show her.
   */
  getPickupCode(bookingId: string): Promise<PickupCodeResponse> {
    return request(`/api/v1/bookings/${bookingId}/pickup-code`);
  },

  /**
   * The caller's own trips, paged and filtered. Every filter is optional
   * and an omitted one does not narrow the list. Category is repeated per
   * value, which is how Spring reads a Set from a query string.
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
 * Booking-scoped chat with the assigned partner.
 * <p>
 * There is no endpoint anywhere that hands a rider a partner's phone
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

  /** 409 ALREADY_RATED on a second attempt, 409 RATING_WINDOW_CLOSED once the window has passed. */
  submit(bookingId: string, stars: number, comment?: string): Promise<Rating> {
    return request(`/api/v1/ratings/bookings/${bookingId}`, { method: 'POST', body: { stars, comment } });
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
