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
  /** What the operator wrote, when the last decision was a rejection. */
  rejectionReason: string | null;
  updatedAt: string;
}

/**
 * How long a review is taking, and whether that is measured or aimed at.
 * <p>
 * The difference matters on screen: "usually about two hours" is a fact
 * about reviews that happened, "we aim to review within four hours" is a
 * promise. Saying the first while meaning the second is a lie to somebody
 * who is waiting to get somewhere.
 */
export interface VerificationTurnaround {
  typicalMinutes: number;
  measured: boolean;
  sampleSize: number;
}

export interface CustomerProfileSummary {
  accountId: string;
  name: string | null;
  /** Null only for a Google-signup account that has not reached the Add your phone number step yet - see AddPhone. */
  phoneNumber: string | null;
  /**
    * Home and Work as places: a label and, once she has put it on the map,
    * a point. lat/lng are null for an address typed in before saved places
    * had coordinates - it still shows, and still cannot be tapped into a
    * booking.
    */
  home: SavedPlace | null;
  work: SavedPlace | null;
  /** YYYY-MM-DD. Null for an account that has not completed its profile since dates of birth were required. */
  dateOfBirth: string | null;
  email: string | null;
  /** Null when there is no photo, or when storage cannot serve one to a browser - render a silhouette. */
  profilePhotoUrl: string | null;
  /** Whether a photo is on file - ask this, not the URL, when deciding whether she still needs to add one. */
  hasProfilePhoto: boolean;
  /** Name, date of birth and photo are all on file. Until then the app sends her to /complete-profile. */
  profileComplete: boolean;
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
  /**
   * When the fare was paid. A COMPLETED trip with this null has ended and is
   * still unpaid - see isAwaitingPayment in the design system.
   */
  paymentSettledAt: string | null;
  /** Taken off by a promotion. The fare itself is never changed - see amountDue. */
  promoDiscount: number;
  /** What she pays: the fare less promoDiscount. */
  amountDue: number;
  promotionName: string | null;
}

/** A promotion she holds - the signup credit, or a code she entered. */
export interface HeldPromotion {
  name: string;
  type: 'SIGNUP_CREDIT' | 'PERCENTAGE_DISCOUNT' | 'FLAT_DISCOUNT';
  creditLeft: number | null;
  creditTotal: number | null;
  usesLeft: number | null;
  expiresAt: string | null;
}

/**
 * An ended trip still waiting for payment. For a rider it blocks her next
 * booking until paid; holdUntil is only set on a partner's.
 */
export interface PaymentHold {
  bookingId: string;
  amount: number;
  completedAt: string;
  holdUntil: string | null;
}

/** Her SheOut wallet: a closed-loop balance, topped up online and spent only on her own trips. */
export interface RiderWallet {
  balance: number;
  minTopup: number;
  maxTopup: number;
  maxBalance: number;
}

export type RiderWalletEntryType = 'TOPUP' | 'TRIP_PAYMENT';

export interface RiderWalletEntry {
  id: string;
  type: RiderWalletEntryType;
  /** Signed: positive added money, negative paid for a trip. */
  amount: number;
  balanceAfter: number;
  bookingId: string | null;
  createdAt: string;
}

/** A top-up's Checkout details - the same shape as a trip's, plus the id to verify it against. */
export interface TopupCheckout extends CheckoutDetails {
  topupId: string;
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

/** An inbox entry - what SheOut told her, not how it was delivered. See the shared NotificationInbox. */
export type { InboxItem as NotificationView, InboxPage, PushConfig } from '@sheout/design-system';

/** A booking's payment. razorpayOrderId/paymentId are null for a CASH payment. */
/** Named so filter controls can enumerate it without repeating the union. */
/** WAIVED marks trips that ended before payment was required to close them. */
export type PaymentStatus = 'PENDING' | 'CAPTURED' | 'FAILED' | 'REFUNDED' | 'WAIVED';

/** CASH is historical only - it is no longer accepted. SHEOUT_WALLET is her own SheOut balance. */
export type PaymentMethod = 'UPI' | 'CASH' | 'CARD' | 'NETBANKING' | 'WALLET' | 'ONLINE' | 'SHEOUT_WALLET' | 'PROMO_CREDIT';

/** What Razorpay Checkout is opened with. amountPaise is the fare in paise, as Razorpay counts it. */
export interface CheckoutDetails {
  keyId: string;
  orderId: string;
  amountPaise: number;
  currency: string;
}

/** What Checkout hands its success handler, sent back to the server to verify. */
export interface CheckoutResult {
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}

export interface PaymentSummary {
  id: string;
  bookingId: string;
  amount: number;
  /**
   * How it was paid, recorded at capture. Before capture it is a placeholder
   * (UPI) and means nothing - read it only when status is CAPTURED. ONLINE is
   * a Checkout method Razorpay reported that none of the others name.
   */
  method: PaymentMethod;
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
  /** What a promotion she holds would take off. 0 when none applies. */
  promoDiscount: number;
  /** fareEstimate less promoDiscount. */
  youPay: number;
  promotionName: string | null;
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

/** How long dispatch may spend searching for one booking, in seconds. */
export interface SearchConfig {
  searchTimeoutSeconds: number;
}

/** See dispatchApi.nearbyDrivers. Positions only - no ids, names or ratings. */
export interface NearbyDrivers {
  drivers: { lat: number; lng: number }[];
  radiusKm: number;
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

/** One device signed in to this account - see sessionsApi. */
export interface AccountSession {
  id: string;
  device: string;
  signedInAt: string;
  lastActiveAt: string;
  /** The device asking. Signing it out is signing out. */
  current: boolean;
}

/** One of her saved places - see CustomerProfileSummary.home / .work. */
export interface SavedPlace {
  label: string;
  lat: number | null;
  lng: number | null;
}
