import { dsT } from '../i18n';
/**
 * The reasons a trip can be cancelled, in the words each side would use.
 * <p>
 * A fixed set rather than a free-text box, because the point of asking is
 * to be able to count the answers: a rider who changed her mind and a rider
 * whose partner never arrived are not the same event, and free text cannot
 * tell them apart at scale. "Other" keeps the honest escape hatch and asks
 * for a sentence, because an "Other" with nothing after it is no reason at
 * all wearing the costume of one.
 * <p>
 * Both apps read from here so the two lists cannot drift into asking the
 * same question two different ways.
 */

/** Mirrors the backend's CancellationReason exactly. */
export type CancellationReason =
  | 'CHANGE_OF_PLANS'
  | 'DRIVER_TAKING_TOO_LONG'
  | 'FOUND_ANOTHER_RIDE'
  | 'WRONG_PICKUP_LOCATION'
  | 'CUSTOMER_NOT_AT_PICKUP'
  | 'DRIVER_UNAVAILABLE'
  | 'OTHER';

const REASON_LABELS: Record<CancellationReason, string> = {
  CHANGE_OF_PLANS: 'My plans changed',
  DRIVER_TAKING_TOO_LONG: 'The partner is taking too long',
  FOUND_ANOTHER_RIDE: 'I found another ride',
  WRONG_PICKUP_LOCATION: 'The pickup point is wrong',
  CUSTOMER_NOT_AT_PICKUP: 'The rider was not at the pickup point',
  DRIVER_UNAVAILABLE: 'I cannot complete this trip',
  OTHER: 'Something else',
};

export function cancellationReasonLabel(reason: string | null | undefined): string {
  if (!reason) return dsT('cancellation.none', 'No reason recorded');
  return dsT(`cancellation.${reason}`, REASON_LABELS[reason as CancellationReason] ?? reason);
}

export interface CancellationReasonOption {
  value: CancellationReason;
  label: string;
}

function optionsFor(values: CancellationReason[]): CancellationReasonOption[] {
  // A getter, so the label is read in the current language each time it is
  // shown rather than frozen in whichever language the app started in.
  return values.map((value) => ({
    value,
    get label() {
      return cancellationReasonLabel(value);
    },
  }));
}

/**
 * What a rider is asked.
 * <p>
 * She is never offered a reason that blames her - "the rider was not at the
 * pickup point" is a partner's account of events, and putting it in her own
 * list would only produce noise in the numbers an operator later reads.
 */
export const CUSTOMER_CANCELLATION_REASONS: CancellationReasonOption[] = optionsFor([
  'CHANGE_OF_PLANS',
  'DRIVER_TAKING_TOO_LONG',
  'FOUND_ANOTHER_RIDE',
  'WRONG_PICKUP_LOCATION',
  'OTHER',
]);

/** What a partner is asked. Same reasoning, the other way round. */
export const DRIVER_CANCELLATION_REASONS: CancellationReasonOption[] = optionsFor([
  'CUSTOMER_NOT_AT_PICKUP',
  'WRONG_PICKUP_LOCATION',
  'DRIVER_UNAVAILABLE',
  'OTHER',
]);

/** OTHER, and only OTHER, needs a sentence with it. Matches the backend rule. */
export function reasonRequiresNote(reason: CancellationReason | null): boolean {
  return reason === 'OTHER';
}
