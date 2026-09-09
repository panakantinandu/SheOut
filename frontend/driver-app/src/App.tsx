import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './layout/AppShell'
import { Bookings } from './screens/Bookings'
import { Earnings } from './screens/Earnings'
import { Home } from './screens/Home'
import { Login } from './screens/Login'
import { Offer } from './screens/Offer'
import { Profile } from './screens/Profile'
import { Splash } from './screens/Splash'
import { Trip } from './screens/Trip'
import { Verification } from './screens/Verification'

// Dashboard-level screens (Home/Earnings/Bookings/Profile) get the bottom
// tab bar via AppShell; Verification, the New Request offer screen, and
// the active Trip screen push on top with just a back-arrow header
// instead, same split customer-app uses between shell screens and
// booking-flow/tracking screens.
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
      <Route path="/earnings" element={shell(<Earnings />)} />
      <Route path="/bookings" element={shell(<Bookings />)} />
      <Route path="/profile" element={shell(<Profile />)} />

      <Route path="/verification" element={protectedOnly(<Verification />)} />
      <Route path="/offer/:bookingId" element={protectedOnly(<Offer />)} />
      <Route path="/trip/:bookingId" element={protectedOnly(<Trip />)} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
