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
export type BookingStatus =
  | 'REQUESTED'
  | 'MATCHED'
  | 'ACCEPTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  /** Dispatch searched, found nobody and stopped. Not a cancellation - see the backend enum. */
  | 'NO_DRIVERS_AVAILABLE';

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
  /**
   * CONTACTS_RECENTLY_ALERTED comes with success: true. The alert and its new
   * location were recorded, but her contacts had already been texted within
   * the last minute after several presses, so they were not texted again yet.
   */
  reason: 'NO_EMERGENCY_CONTACTS' | 'ALL_SENDS_FAILED' | 'CONTACTS_RECENTLY_ALERTED' | null;
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
    | 'SOS_ALERT'
    | 'SUPPORT_REPLY';
  channel: 'SMS' | 'PUSH' | 'EMAIL';
  status: 'SENT' | 'FAILED';
  failureReason: string | null;
  createdAt: string;
}

/** A booking's payment. razorpayOrderId/paymentId are null for a CASH payment. */
/** Named so filter controls can enumerate it without repeating the union. */
export type PaymentStatus = 'PENDING' | 'CAPTURED' | 'FAILED' | 'REFUNDED';

export interface PaymentSummary {
  id: string;
  bookingId: string;
  amount: number;
  method: 'UPI' | 'CASH';
  status: PaymentStatus;
  razorpayOrderId: string | null;
  razorpayPaymentId: string | null;
  failureReason: string | null;
  /**
   * What the partner receives, and the rate that produced it. Both null
   * until capture - nothing is owed before the rider has paid. The rider's
   * price and the platform's margin stay two separate numbers rather than
   * the margin being folded into a higher price.
   */
  driverPayout: number | null;
  commissionPercent: number | null;
  createdAt: string;
  updatedAt: string;
  capturedAt: string | null;
}

/**
 * The driver's last REPORTED position - not an interpolated or predicted
 * one. recordedAt is when dispatch actually received it, so the UI can show
 * how stale the marker is instead of implying it is live.
 */
export interface DriverLocation {
  lat: number;
  lng: number;
  recordedAt: string;
}

/**
 * The code she reads out to her partner before getting in.
 * <p>
 * Served only to the customer, and only while the booking is ACCEPTED. It is
 * deliberately not part of BookingSummary: that is served to the driver too,
 * and a partner who can read the code is not proving anything by typing it.
 */
export interface PickupCodeResponse {
  pickupCode: string;
}

/**
 * A price for a trip that has not been created. fareEstimate is the exact
 * amount the booking will be created with, not an approximation of it.
 * distanceKm is straight-line, the distance the fare was derived from,
 * rounded to one decimal by the backend.
 */
/**
 * A price and how it was reached.
 * <p>
 * The breakdown comes back, not just the total, so a rider asking why a
 * short trip cost what it did can be answered. routed says whether the
 * distance is a real road route or a fallback estimate - a guess must never
 * be shown as a measurement.
 */
export interface FareQuote {
  fareEstimate: number;
  distanceKm: number;
  durationMinutes: number;
  routed: boolean;
  baseFare: number;
  distanceCharge: number;
  timeCharge: number;
  surgeMultiplier: number;
  nightMultiplier: number;
  minimumFareApplied: boolean;
  category: BookingCategory;
}

/**
 * One page of a list - the shape every paged endpoint returns. Mirrors
 * PageResponse on the backend.
 */
export interface PagedResult<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  hasMore: boolean;
}

export type CancellationReason =
  | 'CHANGE_OF_PLANS'
  | 'DRIVER_TAKING_TOO_LONG'
  | 'FOUND_ANOTHER_RIDE'
  | 'WRONG_PICKUP_LOCATION'
  | 'CUSTOMER_NOT_AT_PICKUP'
  | 'DRIVER_UNAVAILABLE'
  | 'OTHER';

export interface ChatMessage {
  id: string;
  bookingId: string;
  senderAccountId: string;
  senderRole: AccountRole;
  body: string;
  sentAt: string;
}

export interface ChatThreadResponse {
  messages: ChatMessage[];
  open: boolean;
  supportPhoneNumber: string;
}

/** What GET /support/contact returns. phoneNumber is null when none is configured. */
export interface SupportContact {
  phoneNumber: string | null;
}

export type { SupportTicketCategory, SupportTicketStatus } from '@sheout/design-system';
import type { SupportTicketCategory, SupportTicketStatus } from '@sheout/design-system';

/**
 * A support ticket as its raiser sees it. There is no assignee or resolver
 * here: which operator is handling it is not shown to riders or partners.
 */
export interface SupportTicket {
  id: string;
  category: SupportTicketCategory;
  subject: string;
  description: string;
  linkedBookingId: string | null;
  status: SupportTicketStatus;
  priority: 'LOW' | 'MEDIUM' | 'HIGH';
  createdAt: string;
  lastActivityAt: string;
  resolvedAt: string | null;
}

/** One line of a ticket thread. Operators' internal notes are never sent to the app. */
export interface SupportTicketMessage {
  id: string;
  message: string;
  mine: boolean;
  fromSupport: boolean;
  createdAt: string;
}

/** open is false once the ticket is CLOSED. */
export interface SupportTicketThreadResponse {
  ticket: SupportTicket;
  messages: SupportTicketMessage[];
  open: boolean;
}

/**
 * One rating slot. stars is null while it is still open, which is how a
 * client tells "not rated yet" from "rated" without a second call.
 */
export interface Rating {
  id: string;
  bookingId: string;
  raterAccountId: string;
  ratedAccountId: string;
  raterRole: AccountRole;
  stars: number | null;
  comment: string | null;
  submittedAt: string | null;
  rateableUntil: string;
}

/** averageStars is null when the account has never been rated - not the same as a low score. */
export interface AggregateRating {
  averageStars: number | null;
  totalRatings: number;
}

/** How long dispatch may spend searching for one booking, in seconds. */
export interface SearchConfig {
  searchTimeoutSeconds: number;
}

/** The vehicle a partner drives. Mirrors the backend enum. */
export type VehicleType = 'BIKE' | 'AUTO' | 'CAB';

/**
 * The assigned partner, as a rider is allowed to see her.
 * Served only from ACCEPTED onwards - never during MATCHED.
 */
export interface AssignedDriver {
  name: string | null;
  /** Null when she has no photo yet; render a silhouette, never a broken image. */
  photoUrl: string | null;
  vehicleType: VehicleType | null;
  vehicleRegistrationNumber: string | null;
  /** Null when nobody has rated her - not the same as a low score. */
  averageStars: number | null;
  totalRatings: number;
}
