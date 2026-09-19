import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import './index.css'
import { initErrorReporting } from '@sheout/design-system'
import { registerAppUpdates } from './lib/appUpdates'
import { routeColdStartThroughSplash } from '@sheout/design-system'
import './i18n'

// First, so a crash in anything below is still reported. Does nothing at all
// unless VITE_SENTRY_DSN was set at build time.
initErrorReporting({ dsn: import.meta.env.VITE_SENTRY_DSN, environment: import.meta.env.MODE })

// Before the router reads the URL: a cold launch of the installed app goes
// through Splash first, wherever it was opened - see coldStart.ts.
routeColdStartThroughSplash()

registerAppUpdates()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
