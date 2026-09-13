import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { LocationBroadcastProvider } from './lib/LocationBroadcastContext'
import './index.css'

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
