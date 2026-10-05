import type { PartnerDocumentType } from '../api/types';

/**
 * The shapes the server accepts (DocumentNumberRules,
 * VehicleRegistrationNumber), checked as she types so she is told at once,
 * not after an upload. The server checks again; this only says it sooner.
 */

const STATE_CODES = new Set([
  'AN', 'AP', 'AR', 'AS', 'BR', 'CG', 'CH', 'DD', 'DL', 'DN', 'GA', 'GJ', 'HP', 'HR', 'JH', 'JK', 'KA', 'KL', 'LA', 'LD',
  'MH', 'ML', 'MN', 'MP', 'MZ', 'NL', 'OD', 'OR', 'PB', 'PY', 'RJ', 'SK', 'TG', 'TN', 'TR', 'TS', 'UA', 'UK', 'UP', 'WB',
]);

/** The longest a number can be typed, spaces included, per document. */
export const NUMBER_MAX_LENGTH: Record<PartnerDocumentType, number> = {
  DRIVING_LICENCE: 20,
  VEHICLE_RC: 14,
  VEHICLE_INSURANCE: 36,
  PUC: 30,
  FITNESS_CERTIFICATE: 30,
  POLICE_CERTIFICATE: 36,
  BACKGROUND_CHECK_REPORT: 36,
};

/** Upper case, no spaces; licences and plates also without hyphens - as the server stores it. */
export function normalizeNumber(type: PartnerDocumentType, raw: string): string {
  const upper = raw.trim().toUpperCase().replace(/\s+/g, '');
  return type === 'DRIVING_LICENCE' || type === 'VEHICLE_RC' ? upper.replace(/-/g, '') : upper;
}

export function isPlate(raw: string): boolean {
  const n = raw.toUpperCase().replace(/[\s-]/g, '');
  if (/^\d{2}BH\d{4}[A-Z]{1,2}$/.test(n)) return true;
  const m = /^([A-Z]{2})\d{1,2}[A-Z]{1,3}\d{4}$/.exec(n);
  return Boolean(m && STATE_CODES.has(m[1]));
}

function isLicence(n: string): boolean {
  const m = /^([A-Z]{2})(\d{2,3})(\d{4})(\d{7})$/.exec(n);
  if (!m || !STATE_CODES.has(m[1])) return false;
  const year = Number(m[3]);
  return year >= 1950 && year <= new Date().getFullYear();
}

function isReference(n: string, min: number, max: number): boolean {
  return n.length >= min && n.length <= max && /^[A-Z0-9][A-Z0-9/-]*$/.test(n) && /\d/.test(n);
}

/** True when the number is in a shape this document can have; '' is "not typed yet", not wrong. */
export function numberLooksRight(type: PartnerDocumentType, raw: string): boolean {
  const n = normalizeNumber(type, raw);
  switch (type) {
    case 'DRIVING_LICENCE':
      return isLicence(n);
    case 'VEHICLE_RC':
      return isPlate(n);
    case 'VEHICLE_INSURANCE':
      return isReference(n, 6, 30);
    case 'PUC':
    case 'FITNESS_CERTIFICATE':
      return isReference(n, 6, 25);
    default:
      return isReference(n, 4, 30);
  }
}

/** The furthest-ahead valid-until date (yyyy-mm-dd) a document of this kind is issued with, from today. */
export function latestValidUntil(type: PartnerDocumentType, today: string): string | null {
  const years: Partial<Record<PartnerDocumentType, number>> = {
    DRIVING_LICENCE: 20,
    VEHICLE_RC: 15,
    VEHICLE_INSURANCE: 5,
    PUC: 1,
    FITNESS_CERTIFICATE: 2,
  };
  const y = years[type];
  if (y === undefined) return null;
  const d = new Date(`${today}T00:00:00Z`);
  d.setUTCFullYear(d.getUTCFullYear() + y);
  if (type !== 'DRIVING_LICENCE' && type !== 'VEHICLE_RC') d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

/** The oldest a police certificate can be and still be accepted: re-verification is every 12 months by default. */
export function earliestPoliceIssue(today: string, months = 12): string {
  const d = new Date(`${today}T00:00:00Z`);
  d.setUTCMonth(d.getUTCMonth() - months);
  return d.toISOString().slice(0, 10);
}
