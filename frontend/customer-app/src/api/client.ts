import type {
  ApiErrorResponse,
  AuthSession,
  BookingCategory,
  BookingSummary,
  BookingType,
  CustomerProfileSummary,
  EmergencyContact,
  GeoAddress,
  VerificationSummary,
} from './types';

// VITE_API_BASE_URL lets each deployment point at its own backend (Vercel
// env var for prod, .env.local for local dev override) - hardcoding
// localhost:8080 here would make the deployed app unusable, since that
// only resolves on the machine running the backend, not a visitor's browser.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const TOKEN_STORAGE_KEY = 'sheout_access_token';

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
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = getStoredToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
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

  logout(): void {
    setStoredToken(null);
  },
};

export const usersApi = {
  getMyProfile(): Promise<CustomerProfileSummary> {
    return request('/api/v1/users/customer/me');
  },

  updateMyProfile(update: { name: string; homeAddress?: string; workAddress?: string }): Promise<CustomerProfileSummary> {
    return request('/api/v1/users/customer/me', { method: 'PUT', body: update });
  },

  getMyEmergencyContacts(): Promise<EmergencyContact[]> {
    return request('/api/v1/users/customer/me/emergency-contacts');
  },
};

export const verificationApi = {
  getMyStatus(): Promise<VerificationSummary> {
    return request('/api/v1/driver-verification/me');
  },
};

export const bookingApi = {
  create(input: { type: BookingType; category: BookingCategory; pickup: GeoAddress; drop: GeoAddress }): Promise<BookingSummary> {
    return request('/api/v1/bookings', { method: 'POST', body: input });
  },

  getById(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}`);
  },

  listMine(): Promise<BookingSummary[]> {
    return request('/api/v1/bookings/me');
  },

  cancel(bookingId: string): Promise<BookingSummary> {
    return request(`/api/v1/bookings/${bookingId}/cancel`, { method: 'POST' });
  },
};
