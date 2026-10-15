import type { Plugin } from 'vite';

/** Every legal-entity.config.json value still TODO_FROM_OWNER, by path. */
export function missingLegalValues(config?: unknown): string[];
/** Warns on a production build with missing business details; fails it when LEGAL_STRICT_BUILD=true. */
export function legalEntityGuard(): Plugin;
