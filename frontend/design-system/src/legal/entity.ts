import config from './legal-entity.config.json';

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
  grievanceOfficer: { name: string; designation: string; email: string; basedIn: string };
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
