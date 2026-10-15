import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { legalEntityGuard } from '@sheout/design-system/legal-guard'

// The public site at sheoutride.com. A plain static build: no service worker,
// no API calls - it only sends people on to the two apps.
export default defineConfig({
  plugins: [react(), legalEntityGuard()],
  server: { port: 5176 },
})
