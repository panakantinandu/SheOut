import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { LocationBroadcastProvider } from './lib/LocationBroadcastContext'
import './index.css'
import { initErrorReporting, setMapServiceArea } from '@sheout/design-system'
import { serviceAreaApi } from './api/client'
import { registerAppUpdates } from './lib/appUpdates'
import { captureReferralFromUrl, routeColdStartThroughSplash } from '@sheout/design-system'
import './i18n'

// First, so a crash in anything below is still reported. Does nothing at all
// unless VITE_SENTRY_DSN was set at build time.
initErrorReporting({ dsn: import.meta.env.VITE_SENTRY_DSN, environment: import.meta.env.MODE })

// Before the router reads the URL: a cold launch of the installed app goes
// through Splash first, wherever it was opened - see coldStart.ts.
// A friend's invite link (?ref=CODE) is kept for the signup screen, and
// taken off the URL, before anything else reads it.
captureReferralFromUrl()
routeColdStartThroughSplash()

registerAppUpdates()

// The service boundary, from the server, so her maps show where SheOut
// operates and nothing beyond. Not awaited; on failure the default stands.
serviceAreaApi.get().then((a) => setMapServiceArea({ lat: a.centreLat, lng: a.centreLng }, a.radiusKm)).catch(() => undefined)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        {/* Above the router on purpose: the GPS subscription must survive
            navigation between Home, Offer and Trip. See
            LocationBroadcastContext for what broke when it did not. */}
        <LocationBroadcastProvider>
          <App />
        </LocationBroadcastProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
