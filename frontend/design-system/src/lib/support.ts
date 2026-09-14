import type { StatusTone } from '../components/StatusBadge';
import { humanizeEnum } from './labels';

/**
 * Words and colours for support tickets, shared by both apps so a ticket
 * reads the same to a rider and a partner - the same reason labels.ts
 * exists.
 */

export type SupportTicketCategory =
  | 'PAYMENT_DISPUTE'
  | 'DRIVER_OR_CUSTOMER_BEHAVIOR'
  | 'SAFETY_CONCERN'
  | 'APP_ISSUE'
  | 'CANCELLATION_DISPUTE'
  | 'OTHER';

export type SupportTicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

/**
 * The behaviour category is worded for whoever is reading it. A rider
 * reports a partner and a partner reports a rider; "driver or customer
 * behaviour" is the API's name for both and nobody's way of saying either.
 */
export function supportCategoryLabel(category: string, audience: 'customer' | 'driver'): string {
  switch (category) {
    case 'PAYMENT_DISPUTE':
      return audience === 'driver' ? 'Payment or earnings' : 'Payment problem';
    case 'DRIVER_OR_CUSTOMER_BEHAVIOR':
      return audience === 'driver' ? "A rider's behaviour" : "A partner's behaviour";
    case 'SAFETY_CONCERN':
      return 'Safety concern';
    case 'APP_ISSUE':
      return 'Problem with the app';
    case 'CANCELLATION_DISPUTE':
      return 'A cancellation';
    case 'OTHER':
      return 'Something else';
    default:
      return humanizeEnum(category);
  }
}

export function supportCategoryOptions(audience: 'customer' | 'driver') {
  return (
    ['SAFETY_CONCERN', 'PAYMENT_DISPUTE', 'DRIVER_OR_CUSTOMER_BEHAVIOR', 'CANCELLATION_DISPUTE', 'APP_ISSUE', 'OTHER'] as const
  ).map((value) => ({ value, label: supportCategoryLabel(value, audience) }));
}

const STATUS: Record<string, string> = {
  OPEN: 'Waiting for support',
  IN_PROGRESS: 'Being looked at',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

/** Written from the raiser's side: "OPEN" says nothing about whose move it is. */
export function supportStatusLabel(status: string): string {
  return STATUS[status] ?? humanizeEnum(status);
}

export function supportStatusTone(status: string): StatusTone {
  switch (status) {
    case 'OPEN':
      return 'warning';
    case 'IN_PROGRESS':
      return 'primary';
    case 'RESOLVED':
      return 'success';
    default:
      return 'neutral';
  }
}

export const SUPPORT_STATUS_OPTIONS = (['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'] as const).map((value) => ({
  value,
  label: supportStatusLabel(value),
}));

/** Shared with the server's own column limits. */
export const SUPPORT_SUBJECT_MAX = 150;
export const SUPPORT_TEXT_MAX = 2000;
