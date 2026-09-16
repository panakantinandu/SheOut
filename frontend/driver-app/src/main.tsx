import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { LocationBroadcastProvider } from './lib/LocationBroadcastContext'
import './index.css'
import { initErrorReporting } from '@sheout/design-system'
import { registerAppUpdates } from './lib/appUpdates'

// First, so a crash in anything below is still reported. Does nothing at all
// unless VITE_SENTRY_DSN was set at build time.
initErrorReporting({ dsn: import.meta.env.VITE_SENTRY_DSN, environment: import.meta.env.MODE })

registerAppUpdates()

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
