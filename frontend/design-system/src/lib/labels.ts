/**
 * Turns the backend's enum values into words a passenger or a partner can
 * read.
 * <p>
 * These live here because the two apps had drifted. The customer app wrote
 * its own category labels and showed "Bike Taxi"; the driver app showed the
 * enum, so a partner's own dashboard told them their vehicle was "BIKE",
 * their verification was "PENDING" and their trip was "IN PROGRESS". Same
 * product, same data, two different levels of finish - which is what this
 * pass was looking for.
 * <p>
 * Plain string inputs, not the apps' own union types: each app declares its
 * own copy of these enums, and the design system should not have to pick a
 * side. Anything unrecognised falls back to sentence case rather than
 * throwing, so a new backend value degrades to something readable instead
 * of a blank.
 */

/** "IN_PROGRESS" -> "In progress". The last-resort fallback for all of these. */
import { dsT } from '../i18n';

export function humanizeEnum(value: string): string {
  const words = value.replace(/_/g, ' ').trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

const VEHICLE: Record<string, string> = {
  BIKE: 'Bike',
  AUTO: 'Auto',
  CAB: 'Cab',
};

/** A partner's vehicle, as shown on their profile and dashboard. */
export function vehicleLabel(vehicle: string | null | undefined): string {
  if (!vehicle) return dsT('vehicle.unset', 'Vehicle not set');
  return dsT(`vehicle.${vehicle}`, VEHICLE[vehicle] ?? humanizeEnum(vehicle));
}

const VERIFICATION: Record<string, string> = {
  PENDING: 'Not submitted',
  UNDER_REVIEW: 'Under review',
  VERIFIED: 'Verified',
  REJECTED: 'Rejected',
};

/**
 * PENDING reads as "we are working on it", which is the opposite of what it
 * means: nothing has been submitted yet and the person needs to act.
 */
export function verificationStatusLabel(status: string | null | undefined): string {
  if (!status) return dsT('verification.notRequired', 'Not required');
  return dsT(`verification.${status}`, VERIFICATION[status] ?? humanizeEnum(status));
}

const BOOKING_STATUS: Record<string, string> = {
  REQUESTED: 'Finding a partner',
  MATCHED: 'Partner assigned',
  ACCEPTED: 'On the way',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  // Deliberately not phrased as a cancellation. Nobody called this off;
  // the search ended without finding anyone, and telling a rider she
  // cancelled a trip she was waiting for is both wrong and insulting.
  NO_DRIVERS_AVAILABLE: 'No drivers found',
};

export function bookingStatusLabel(status: string | null | undefined): string {
  if (!status) return dsT('common.unknown', 'Unknown');
  return dsT(`bookingStatus.${status}`, BOOKING_STATUS[status] ?? humanizeEnum(status));
}

/**
 * A trip the partner has ended but nobody has paid for yet. Shown as
 * "Awaiting payment", not "Completed": a trip is not over until the fare is
 * in, and calling it completed is what made ending it without payment look
 * like the end of the matter.
 */
export function isAwaitingPayment(booking: { status: string; paymentSettledAt?: string | null }): boolean {
  return booking.status === 'COMPLETED' && !booking.paymentSettledAt;
}

/** The status label for a whole trip, taking payment into account - see isAwaitingPayment. */
export function tripStatusLabel(booking: { status: string; paymentSettledAt?: string | null }): string {
  return isAwaitingPayment(booking) ? dsT('bookingStatus.AWAITING_PAYMENT', 'Awaiting payment') : bookingStatusLabel(booking.status);
}

const CATEGORY: Record<string, string> = {
  BIKE: 'Bike Taxi',
  AUTO: 'Auto Ride',
  CAB: 'Cab Ride',
  PARCEL: 'Parcel Delivery',
  LUNCHBOX: 'Lunch Box Delivery',
};

export function bookingCategoryLabel(category: string | null | undefined): string {
  if (!category) return dsT('category.trip', 'Trip');
  return dsT(`category.${category}`, CATEGORY[category] ?? humanizeEnum(category));
}

/** UPI keeps its capitals - it is the network's name, not an enum. */
export function paymentMethodLabel(method: string | null | undefined): string {
  if (!method) return dsT('paymentMethod.none', 'Not recorded');
  if (method === 'UPI') return 'UPI';
  if (method === 'SHEOUT_WALLET') return dsT('paymentMethod.SHEOUT_WALLET', 'SheOut wallet');
  return dsT(`paymentMethod.${method}`, humanizeEnum(method));
}

const PAYMENT_STATUS: Record<string, string> = {
  PENDING: 'Awaiting payment',
  CAPTURED: 'Paid',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
  // Trips from before a fare had to be paid to close a trip. Not "Paid" -
  // no money moved - and not "Failed", which would suggest she owes it.
  WAIVED: 'Settled earlier',
};

export function paymentStatusLabel(status: string | null | undefined): string {
  if (!status) return dsT('common.unknown', 'Unknown');
  return dsT(`paymentStatus.${status}`, PAYMENT_STATUS[status] ?? humanizeEnum(status));
}
