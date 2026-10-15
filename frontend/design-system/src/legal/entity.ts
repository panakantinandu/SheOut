import config from './legal-entity.config.json';
import { ISO_DATE, officerInEffect, type GrievanceOfficer, type GrievanceRegime, type GrievanceRegimeConfig } from './officers';

export { GRIEVANCE_REGIMES, officerInEffect, type GrievanceOfficer, type GrievanceRegime, type GrievanceRegimeConfig } from './officers';

/**
 * SheOut's business details and its processors, read from
 * legal-entity.config.json - the one place they are written down.
 * <p>
 * A value the owner has not supplied yet is the literal TODO_FROM_OWNER in
 * the file. It is shown to readers as a visible placeholder, never as a
 * blank and never as a guess; a strict production build refuses to finish
 * while any remain (legalEntityGuard in legal-check.js).
 */
export const TODO_FROM_OWNER = 'TODO_FROM_OWNER';

/** How an unsupplied value reads on a page. Rendered in the danger colour by LegalDocumentView. */
export const TODO_LEGAL = '[to be completed by SheOut]';

/** The value, or the visible placeholder if the owner has not supplied it. */
export function entityValue(value: string): string {
  return value === TODO_FROM_OWNER ? TODO_LEGAL : value;
}

export type ProcessorKey =
  | 'render'
  | 'vercel'
  | 'googleMaps'
  | 'googleFonts'
  | 'googleSignIn'
  | 'firebase'
  | 'razorpay'
  | 'aws'
  | 'nominatim'
  | 'anthropic'
  | 'sentry'
  | 'sms';

export interface LegalEntity {
  brandLine: string;
  legalName: string;
  cin: string;
  registeredAddress: string;
  supportEmail: string;
  supportPhone: string;
  grievanceOfficers: Record<GrievanceRegime, GrievanceRegimeConfig> & { _comment?: string };
  courts: string;
  dataLocation: string;
  grievanceAcknowledgeHours: number;
  grievanceResolveDays: number;
  refundToSourceWorkingDays: number;
  processors: { key: ProcessorKey; live: boolean }[];
}

export const LEGAL_ENTITY = config as LegalEntity;

/** True for a processor SheOut really uses today. Only these are named in the Privacy Policy. */
export function isProcessorLive(key: ProcessorKey): boolean {
  return LEGAL_ENTITY.processors.some((p) => p.key === key && p.live);
}

/** The officer for a regime on a given day (see officerInEffect). */
export function grievanceOfficer(regime: GrievanceRegime, today: Date = new Date(), entity: LegalEntity = LEGAL_ENTITY): GrievanceOfficer {
  return officerInEffect(entity.grievanceOfficers[regime].appointments, today);
}

/** "Name, Designation, email (since 1 November 2026)" - with placeholders where the owner has not supplied a value. */
export function grievanceLine(regime: GrievanceRegime, today: Date = new Date()): string {
  const o = grievanceOfficer(regime, today);
  const since = ISO_DATE.test(o.effectiveFrom)
    ? ` (since ${new Date(`${o.effectiveFrom}T00:00:00Z`).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })})`
    : '';
  return `${entityValue(o.name)}, ${o.designation}, ${o.email}${since}`;
}

/** Every config value still waiting for the owner, by its path - for the build guard and tests. */
export function missingEntityValues(entity: LegalEntity = LEGAL_ENTITY): string[] {
  const missing: string[] = [];
  const walk = (value: unknown, path: string) => {
    if (value === TODO_FROM_OWNER) missing.push(path);
    else if (value && typeof value === 'object') {
      for (const [k, v] of Object.entries(value)) walk(v, path ? `${path}.${k}` : k);
    }
  };
  walk(entity, '');
  return missing;
}
