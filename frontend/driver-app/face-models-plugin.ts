import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import type { Plugin } from 'vite';

/**
 * Serves the three face-api models the start-of-shift selfie needs at
 * /models/face/, straight out of the installed package - in dev from
 * node_modules, in a build as emitted files. The weights are not copied
 * into the repository: they are the package's, versioned with it.
 * <p>
 * Only these three: a small face finder, the 68-point landmarks that line
 * the face up, and the recogniser that turns it into 128 numbers. The
 * package's age/gender model is deliberately left out - see ShiftCheck.
 */
const FILES = [
  'tiny_face_detector_model-weights_manifest.json',
  'tiny_face_detector_model.bin',
  'face_landmark_68_model-weights_manifest.json',
  'face_landmark_68_model.bin',
  'face_recognition_model-weights_manifest.json',
  'face_recognition_model.bin',
];

const PREFIX = '/models/face/';

export function faceModels(): Plugin {
  const require = createRequire(import.meta.url);
  const modelDir = join(dirname(require.resolve('@vladmandic/face-api/package.json')), 'model');
  const typeOf = (name: string) => (name.endsWith('.json') ? 'application/json' : 'application/octet-stream');
  return {
    name: 'sheout-face-models',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const path = req.url?.split('?')[0] ?? '';
        const name = path.startsWith(PREFIX) ? path.slice(PREFIX.length) : '';
        if (!FILES.includes(name)) return next();
        res.setHeader('Content-Type', typeOf(name));
        res.end(readFileSync(join(modelDir, name)));
      });
    },
    generateBundle() {
      for (const name of FILES) {
        this.emitFile({ type: 'asset', fileName: `models/face/${name}`, source: readFileSync(join(modelDir, name)) });
      }
    },
  };
}
