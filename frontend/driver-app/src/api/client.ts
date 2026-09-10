import type {
  ApiErrorResponse,
  AuthSession,
  BookingSummary,
  DriverOnlineStatus,
  DriverProfileSummary,
  OfferSummary,
  VehicleType,
  VerificationSummary,
  NotificationView,
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
const TOKEN_STORAGE_KEY = 'sheout_driver_access_token';

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

  logout(): void {
    setStoredToken(null);
  },
};

export const usersApi = {
  getMyProfile(): Promise<DriverProfileSummary> {
    return request('/api/v1/users/driver/me');
  },

  updateMyProfile(update: { name: string; vehicleType: VehicleType; vehicleRegistrationNumber: string }): Promise<DriverProfileSummary> {
    return request('/api/v1/users/driver/me', { method: 'PUT', body: update });
  },

  setOnlineStatus(status: DriverOnlineStatus): Promise<DriverProfileSummary> {
    return request('/api/v1/users/driver/me/status', { method: 'POST', body: { status } });
  },
};

export const notificationsApi = {
  listMine(): Promise<NotificationView[]> {
    return request('/api/v1/notifications/me');
  },
};

export const verificationApi = {
  getMyStatus(): Promise<VerificationSummary> {
    return request('/api/v1/driver-verification/me');
  },

  uploadDocument(file: File): Promise<VerificationSummary> {
    const form = new FormData();
    form.append('file', file);
    return request('/api/v1/driver-verification/documents', { method: 'POST', body: form });
  },
};

export const dispatchApi = {
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

export const bookingApi = {
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

  start(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}/start`, { method: 'POST' });
  },

  complete(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}/complete`, { method: 'POST' });
  },

  cancel(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}/cancel`, { method: 'POST' });
  },
};
