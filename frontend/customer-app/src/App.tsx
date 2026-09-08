import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './layout/AppShell'
import { DeliveryBooking } from './screens/DeliveryBooking'
import { Home } from './screens/Home'
import { Login } from './screens/Login'
import { MyBookings } from './screens/MyBookings'
import { Profile } from './screens/Profile'
import { RideBooking } from './screens/RideBooking'
import { Sos } from './screens/Sos'
import { Splash } from './screens/Splash'
import { Tracking } from './screens/Tracking'
import { Wallet } from './screens/Wallet'

// Screens with the bottom tab bar (Home/Bookings/Wallet/Profile/SOS) get
// wrapped in AppShell; booking-flow and tracking screens push on top with
// just a back-arrow header instead, matching the mockup.
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
      <Route path="/bookings" element={shell(<MyBookings />)} />
      <Route path="/wallet" element={shell(<Wallet />)} />
      <Route path="/profile" element={shell(<Profile />)} />
      <Route path="/sos" element={shell(<Sos />)} />

      <Route path="/book/ride" element={protectedOnly(<RideBooking />)} />
      <Route path="/book/:kind" element={protectedOnly(<DeliveryBooking />)} />
      <Route path="/tracking/:bookingId" element={protectedOnly(<Tracking />)} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
