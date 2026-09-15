import type { Plugin } from 'vite';

/** Pass to workbox.importScripts - the push handler, versioned by content hash. */
export declare const pushWorkerImport: string;

/** Serves push-sw.js in dev and emits it in a build. */
export declare function sheoutPushWorker(): Plugin;
