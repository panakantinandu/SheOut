import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import { pushWorkerImport, sheoutPushWorker } from '@sheout/design-system/push-worker-plugin'

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
        // Take over as soon as installed instead of waiting for every open
        // tab to close. Waiting stranded devices already running a build with
        // no update code: an open tab refreshed three times kept the old app.
        // src/lib/appUpdates.ts decides when the page reloads onto it.
        skipWaiting: true,
        clientsClaim: true,
      },
      manifest: {
        name: 'SheOut Customer',
        short_name: 'SheOut',
        description: 'Women-only ride and delivery platform - Hyderabad',
        theme_color: '#4A1A9E',
        start_url: '/home',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          // Android masks installed icons to its own shape; this one keeps the
          // artwork inside the safe zone so the pin and wheels are not cut off.
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
  server: {
    port: 5173,
  },
})
