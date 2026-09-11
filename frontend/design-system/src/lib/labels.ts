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
  if (!vehicle) return 'Vehicle not set';
  return VEHICLE[vehicle] ?? humanizeEnum(vehicle);
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
  if (!status) return 'Not required';
  return VERIFICATION[status] ?? humanizeEnum(status);
}

const BOOKING_STATUS: Record<string, string> = {
  REQUESTED: 'Finding a partner',
  MATCHED: 'Partner assigned',
  ACCEPTED: 'On the way',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export function bookingStatusLabel(status: string | null | undefined): string {
  if (!status) return 'Unknown';
  return BOOKING_STATUS[status] ?? humanizeEnum(status);
}

const CATEGORY: Record<string, string> = {
  BIKE: 'Bike Taxi',
  AUTO: 'Auto Ride',
  CAB: 'Cab Ride',
  PARCEL: 'Parcel Delivery',
  LUNCHBOX: 'Lunch Box Delivery',
};

export function bookingCategoryLabel(category: string | null | undefined): string {
  if (!category) return 'Trip';
  return CATEGORY[category] ?? humanizeEnum(category);
}

/** UPI keeps its capitals - it is the network's name, not an enum. */
export function paymentMethodLabel(method: string | null | undefined): string {
  if (!method) return 'Not recorded';
  return method === 'UPI' ? 'UPI' : humanizeEnum(method);
}

const PAYMENT_STATUS: Record<string, string> = {
  PENDING: 'Awaiting payment',
  CAPTURED: 'Paid',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
};

export function paymentStatusLabel(status: string | null | undefined): string {
  if (!status) return 'Unknown';
  return PAYMENT_STATUS[status] ?? humanizeEnum(status);
}
