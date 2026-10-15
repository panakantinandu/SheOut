/**
 * Grievance officers, one list of appointments per regime (configured in
 * legal-entity.config.json). Kept apart from entity.ts, with no config
 * import, so the rule for "who is in effect" can be tested on its own.
 */

/** The grievance regimes SheOut names an officer for. Which apply is for the lawyer to confirm. */
export type GrievanceRegime = 'consumerEcommerce' | 'dataProtection' | 'itIntermediary';

export const GRIEVANCE_REGIMES: GrievanceRegime[] = ['consumerEcommerce', 'dataProtection', 'itIntermediary'];

export interface GrievanceOfficer {
  name: string;
  designation: string;
  email: string;
  basedIn: string;
  /** YYYY-MM-DD, the first day of the appointment, or TODO_FROM_OWNER. */
  effectiveFrom: string;
}

export interface GrievanceRegimeConfig {
  /** The rule that requires the officer, as cited on the page. */
  rule: string;
  /** What this officer handles, completing "For ...:". */
  handles: string;
  /** Every appointment, oldest first. Past ones stay as the record; a change is one added entry. */
  appointments: GrievanceOfficer[];
}

const PLACEHOLDER = 'TODO_FROM_OWNER';
export const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * The appointment in effect on a day: the latest one whose effectiveFrom has
 * arrived. One whose date is still a placeholder counts as current until a
 * dated one has started. If every appointment starts later, the result is a
 * placeholder - a page never names someone before they take office.
 */
export function officerInEffect(appointments: GrievanceOfficer[], today: Date = new Date()): GrievanceOfficer {
  const day = today.toISOString().slice(0, 10);
  const started = appointments.filter((a) => a.effectiveFrom === PLACEHOLDER || (ISO_DATE.test(a.effectiveFrom) && a.effectiveFrom <= day));
  const dated = started.filter((a) => a.effectiveFrom !== PLACEHOLDER).sort((a, b) => a.effectiveFrom.localeCompare(b.effectiveFrom));
  const current = dated[dated.length - 1] ?? started[started.length - 1];
  return current ?? { ...appointments[0], name: PLACEHOLDER, effectiveFrom: PLACEHOLDER };
}
