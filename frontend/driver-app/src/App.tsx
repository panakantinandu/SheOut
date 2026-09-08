import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './layout/AppShell'
import { Earnings } from './screens/Earnings'
import { Home } from './screens/Home'
import { Login } from './screens/Login'
import { Profile } from './screens/Profile'
import { Splash } from './screens/Splash'
import { Trip } from './screens/Trip'
import { Trips } from './screens/Trips'
import { Verification } from './screens/Verification'

// Dashboard-level screens (Home/Trips/Earnings/Profile) get the bottom tab
// bar via AppShell; Verification and the active Trip screen push on top
// with just a back-arrow header instead, same split customer-app uses
// between shell screens and booking-flow/tracking screens.
function shell(element: JSX.Element) {
  return (
    <ProtectedRoute>
      <AppShell>{element}</AppShell>
    </ProtectedRoute>
  )
}

function protectedOnly(element: JSX.Element) {
  return <ProtectedRoute>{element}</ProtectedRoute>
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<Splash />} />
      <Route path="/login" element={<Login />} />

      <Route path="/home" element={shell(<Home />)} />
      <Route path="/trips" element={shell(<Trips />)} />
      <Route path="/earnings" element={shell(<Earnings />)} />
      <Route path="/profile" element={shell(<Profile />)} />

      <Route path="/verification" element={protectedOnly(<Verification />)} />
      <Route path="/trip/:bookingId" element={protectedOnly(<Trip />)} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
