import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import { pushWorkerImport, sheoutPushWorker } from '@sheout/design-system/push-worker-plugin'

// TODO: replace icons/icon-192.png and icons/icon-512.png with real
// generated app icons before shipping - placeholders only for now.
export default defineConfig({
  plugins: [
    react(),
    sheoutPushWorker(),
    VitePWA({
      // 'prompt', not 'autoUpdate': the app decides WHEN a new version is
      // applied - see src/lib/appUpdates.ts - instead of a reload landing in
      // the middle of a payment.
      registerType: 'prompt',
      injectRegister: false,
      workbox: {
        // The push notification handler, versioned by content hash - see
        // design-system/push/vite-plugin.mjs for why the hash matters.
        importScripts: [pushWorkerImport],
        // The app shell (index.html) is precached and served from cache, so a
        // new deployment only reaches an installed app once a new worker is
        // installed and applied - appUpdates.ts makes that happen on the
        // next open. Old caches are dropped as soon as it is.
        cleanupOutdatedCaches: true,
      },
      manifest: {
        name: 'SheOut Customer',
        short_name: 'SheOut',
        description: 'Women-only ride and delivery platform - Hyderabad',
        theme_color: '#ffffff',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
    }),
  ],
  server: {
    port: 5173,
  },
})
