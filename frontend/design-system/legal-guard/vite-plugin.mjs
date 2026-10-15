// Production builds and SheOut's business details.
//
// Every legal page and footer reads legal-entity.config.json. A value the
// owner has not supplied is the literal TODO_FROM_OWNER and shows on the page
// as "[to be completed by SheOut]". This plugin makes that impossible to miss
// at build time:
//
//   - always: a warning naming every missing value;
//   - LEGAL_STRICT_BUILD=true: the production build fails instead.
//
// Strict is off by default so a build can still ship while the company
// details are being registered (docs/legal-open-items.md). Turn it on in
// Vercel once they are in, so a placeholder can never reach production again.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const CONFIG = fileURLToPath(new URL('../src/legal/legal-entity.config.json', import.meta.url));

export function missingLegalValues(config = JSON.parse(readFileSync(CONFIG, 'utf8'))) {
  const missing = [];
  const walk = (value, path) => {
    if (value === 'TODO_FROM_OWNER') missing.push(path);
    else if (value && typeof value === 'object') for (const [k, v] of Object.entries(value)) walk(v, path ? `${path}.${k}` : k);
  };
  walk(config, '');
  return missing;
}

export function legalEntityGuard() {
  return {
    name: 'sheout-legal-entity-guard',
    apply: 'build',
    buildStart() {
      const missing = missingLegalValues();
      if (!missing.length) return;
      const message =
        `SheOut business details missing in design-system/src/legal/legal-entity.config.json: ${missing.join(', ')}. ` +
        'The legal pages show "[to be completed by SheOut]" for each.';
      if (process.env.LEGAL_STRICT_BUILD === 'true') this.error(`${message} LEGAL_STRICT_BUILD=true, so this build stops.`);
      else this.warn(message);
    },
  };
}
