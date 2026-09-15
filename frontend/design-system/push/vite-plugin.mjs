// Serves push-sw.js to both apps: from memory in dev, as an emitted file in
// a build. Plain .mjs, not TypeScript, because vite.config.ts loads it with
// Node directly.
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('./push-sw.js', import.meta.url), 'utf8');
const version = createHash('sha256').update(source).digest('hex').slice(0, 12);

/**
 * What to pass to workbox.importScripts. The query string carries a hash of
 * the handler: Workbox does not revision imported scripts, so without it a
 * change to push-sw.js would leave the generated sw.js byte-identical, the
 * browser would never install a new worker, and installed apps would keep
 * the old handler forever.
 */
export const pushWorkerImport = `push-sw.js?v=${version}`;

export function sheoutPushWorker() {
  return {
    name: 'sheout-push-worker',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url && req.url.split('?')[0] === '/push-sw.js') {
          res.setHeader('Content-Type', 'application/javascript');
          res.end(source);
          return;
        }
        next();
      });
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'push-sw.js', source });
    },
  };
}
