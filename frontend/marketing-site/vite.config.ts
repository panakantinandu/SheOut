import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The public site at sheoutride.com. A plain static build: no service worker,
// no API calls - it only sends people on to the two apps.
export default defineConfig({
  plugins: [react()],
  server: { port: 5176 },
})
