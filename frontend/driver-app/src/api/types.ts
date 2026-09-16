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
  /**
   * Her PAN, for payout tax compliance. Null when she has not given one -
   * it is optional, and nothing is withheld for its absence.
   */
  panNumber: string | null;
  onlineStatus: DriverOnlineStatus;
  verified: boolean;
  /**
   * Null until she uploads one. Required before going online for the first
   * time, and rendered as a silhouette rather than a broken image until
   * then.
   */
  profilePhotoUrl: string | null;
  /**
   * Whether a photo exists at all - NOT the same as profilePhotoUrl being
   * set. With local-disk storage the file cannot be served to a browser, so
   * the URL is null even though she has uploaded one. Ask this when the
   * question is "does she still need to add one"; use the URL only to draw
   * it.
   */
  hasProfilePhoto: boolean;
  /** YYYY-MM-DD. Null for an account that has not completed its profile since dates of birth were required. */
  dateOfBirth: string | null;
  email: string | null;
  /** Name, date of birth and photo are all on file. Until then the app sends her to /complete-profile. */
  profileComplete: boolean;
  updatedAt: string;
}

/**
 * Which leg of the trip a route is for. The server derives it from the
 * booking's status, so a client can never ask for - or draw - the wrong one.
 */
export type TripPhase = 'PICKUP' | 'DROP';

/**
 * The road to draw, plus where it ends.
 * <p>
 * points is empty and the two figures are null when the router could not be
 * reached. Draw nothing in that case: a straight line between two points in
 * Hyderabad regularly crosses a lake, and a partner reading it as a road is
 * worse off than one told there is no route to show.
 */
export interface TripRoute {
  phase: TripPhase;
  destinationLat: number;
  destinationLng: number;
  destinationLabel: string;
  points: { lat: number; lng: number }[];
  distanceKm: number | null;
  durationMinutes: number | null;
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
/** An inbox entry - what SheOut told her, not how it was delivered. See the shared NotificationInbox. */
export type { InboxItem as NotificationView, InboxPage, PushConfig } from '@sheout/design-system';

/** One page of a list - mirrors PageResponse on the backend. */
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
  /** The DPDP Grievance Officer's email, or null when not yet configured. */
  grievanceOfficerEmail: string | null;
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
  /** The quick reasons tapped with the stars; empty for most ratings. */
  tags: string[];
  submittedAt: string | null;
  rateableUntil: string;
}

/** One tappable reason, as the server's catalogue describes it. */
export interface RatingTagOption {
  code: string;
  label: string;
}

/**
 * What to offer for a good rating and what to offer for a poor one, served
 * by the backend so this app never keeps its own copy of the wording - and
 * so a rider is never shown the partner's list, which would read as nonsense.
 */
export interface RatingTagCatalogue {
  positive: RatingTagOption[];
  negative: RatingTagOption[];
}

/** averageStars is null when the account has never been rated - not the same as a low score. */
export interface AggregateRating {
  averageStars: number | null;
  totalRatings: number;
}

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

export type PaymentStatus = 'PENDING' | 'CAPTURED' | 'FAILED' | 'REFUNDED';

/** Recorded at capture. Before capture it is a placeholder - read it only when status is CAPTURED. */
export type PaymentMethod = 'UPI' | 'CASH' | 'CARD' | 'NETBANKING' | 'WALLET' | 'ONLINE';

/** A trip's payment as the partner sees it. driverPayout is her share, null until the rider has paid. */
export interface PaymentSummary {
  id: string;
  bookingId: string;
  amount: number;
  method: PaymentMethod;
  status: PaymentStatus;
  driverPayout: number | null;
  commissionPercent: number | null;
  capturedAt: string | null;
}

/**
 * Her wallet. availableBalance = totalEarned - cashCollected - totalPaidOut -
 * pendingPayouts, and it can be negative: on a cash trip she already holds the
 * whole fare, so what she owes is SheOut's commission.
 */
export interface WalletSummary {
  totalEarned: number;
  cashCollected: number;
  totalPaidOut: number;
  pendingPayouts: number;
  availableBalance: number;
}

/** Where she is paid. The account number arrives masked; the full number is never sent back to the app. */
export interface PayoutAccountView {
  accountHolderName: string | null;
  accountNumberMasked: string | null;
  ifsc: string | null;
  upiVpa: string | null;
  updatedAt: string | null;
}

export type PayoutStatus = 'PENDING' | 'PAID';

export interface PayoutRequestView {
  id: string;
  amount: number;
  status: PayoutStatus;
  /** Where it goes, already worded for display: "UPI name@bank" or "Bank a/c ending 1234". */
  destination: string;
  requestedAt: string;
  paidAt: string | null;
  paymentReference: string | null;
}

export interface PayoutOverview {
  wallet: WalletSummary;
  account: PayoutAccountView | null;
  requests: PayoutRequestView[];
}

/** A bank account, a UPI ID, or both. Never a card. */
export interface SavePayoutAccount {
  accountHolderName: string | null;
  accountNumber: string | null;
  ifsc: string | null;
  upiVpa: string | null;
  /** Keep the bank account on file and ignore the three bank fields - the app cannot resend a number it only sees masked. */
  keepSavedBankAccount?: boolean;
}
