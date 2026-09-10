// Mirrors the backend's actual JSON shapes exactly (verified against the
// live backend, not guessed from the Java source alone) - see api/client.ts
// for the calls that use these.

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

export type VehicleType = 'BIKE' | 'AUTO' | 'CAB';
export type DriverOnlineStatus = 'ONLINE' | 'OFFLINE';

export interface DriverProfileSummary {
  accountId: string;
  name: string | null;
  phoneNumber: string;
  vehicleType: VehicleType | null;
  vehicleRegistrationNumber: string | null;
  onlineStatus: DriverOnlineStatus;
  verified: boolean;
  updatedAt: string;
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

/**
 * Enriched with the booking's own pickup/drop/fare/category, since a
 * driver who's only been offered this booking (not yet accepted it) isn't
 * a participant on it yet - GET /bookings/{id} would 404 them (see the
 * backend's BookingApi.findById Javadoc), so the offer response itself
 * carries what the Offer screen needs. pickup/drop/fareEstimate/category
 * are null only in the rare case the booking vanished between the offer
 * being made and this being read.
 */
export interface OfferSummary {
  bookingId: string;
  pickup: GeoAddress | null;
  drop: GeoAddress | null;
  fareEstimate: number | null;
  category: BookingCategory | null;
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

/** One entry from the caller's own notification history (GET /notifications/me). */
export interface NotificationView {
  id: string;
  type:
    | 'BOOKING_REQUESTED'
    | 'BOOKING_ACCEPTED'
    | 'BOOKING_COMPLETED'
    | 'BOOKING_CANCELLED'
    | 'ACCOUNT_VERIFIED'
    | 'SOS_ALERT';
  channel: 'SMS' | 'PUSH' | 'EMAIL';
  status: 'SENT' | 'FAILED';
  failureReason: string | null;
  createdAt: string;
}
