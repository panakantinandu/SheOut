// Mirrors the backend's actual JSON shapes exactly (field names/casing
// verified against a live run of the backend, not guessed from the Java
// source alone) - see api/client.ts for the calls that use these.

export type AccountRole = 'CUSTOMER' | 'DRIVER' | 'ADMIN';

export interface AuthSession {
  accessToken: string;
  accountId: string;
  role: AccountRole;
  newAccount: boolean;
}

export type VerificationStatus = 'PENDING' | 'UNDER_REVIEW' | 'VERIFIED' | 'REJECTED';

export interface VerificationSummary {
  accountId: string;
  role: AccountRole;
  genderVerificationStatus: VerificationStatus;
  policeVerificationStatus: VerificationStatus | null;
  documentSubmitted: boolean;
  updatedAt: string;
}

export interface CustomerProfileSummary {
  accountId: string;
  name: string | null;
  /** Null for a Google-signed-in account - phone signup is the only path that collects one. */
  phoneNumber: string | null;
  homeAddress: string | null;
  workAddress: string | null;
  verified: boolean;
  updatedAt: string;
}

export interface EmergencyContact {
  id: string;
  customerAccountId: string;
  name: string;
  phoneNumber: string;
  relationship: string;
}

export type BookingType = 'RIDE' | 'DELIVERY';
export type BookingCategory = 'BIKE' | 'AUTO' | 'CAB' | 'PARCEL' | 'LUNCHBOX';
export type BookingStatus = 'REQUESTED' | 'MATCHED' | 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface GeoAddress {
  label: string;
  lat: number;
  lng: number;
}

export interface BookingSummary {
  id: string;
  type: BookingType;
  category: BookingCategory;
  status: BookingStatus;
  customerId: string;
  driverId: string | null;
  pickup: GeoAddress;
  drop: GeoAddress;
  fareEstimate: number;
  finalFare: number | null;
  requestedAt: string;
  matchedAt: string | null;
  acceptedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
}

export interface SosContactOutcome {
  contactName: string;
  relationship: string;
  delivered: boolean;
}

/**
 * success is true iff at least one contact was actually notified - the
 * field to check regardless of *why* nothing went out (reason is
 * 'NO_EMERGENCY_CONTACTS' or 'ALL_SENDS_FAILED' when success is false).
 * Always returned with a 200 - see the backend's SosController Javadoc for
 * why this doesn't use a non-2xx status for a partial/total send failure.
 */
export interface SosResponse {
  alertId: string;
  contactsTotal: number;
  contactsNotified: number;
  contactsFailed: number;
  contacts: SosContactOutcome[];
  success: boolean;
  reason: 'NO_EMERGENCY_CONTACTS' | 'ALL_SENDS_FAILED' | null;
}

/** The shape GlobalExceptionHandler / ApiException always return on failure. */
export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details: string[];
}
