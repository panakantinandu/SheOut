import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './layout/AppShell'
import { DeliveryBooking } from './screens/DeliveryBooking'
import { Home } from './screens/Home'
import { About } from './screens/About'
import { HelpSupport } from './screens/HelpSupport'
import { Login } from './screens/Login'
import { Notifications } from './screens/Notifications'
import { PaymentMethods } from './screens/PaymentMethods'
import { PersonalDetails } from './screens/PersonalDetails'
import { SavedAddresses } from './screens/SavedAddresses'
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

      {/* Pushed on top of Profile with a back arrow, like the booking flow -
          they are drill-downs, not tab destinations, so no AppShell. */}
      <Route path="/notifications" element={protectedOnly(<Notifications />)} />
      <Route path="/profile/details" element={protectedOnly(<PersonalDetails />)} />
      <Route path="/profile/addresses" element={protectedOnly(<SavedAddresses />)} />
      <Route path="/profile/payments" element={protectedOnly(<PaymentMethods />)} />
      <Route path="/help" element={protectedOnly(<HelpSupport />)} />
      <Route path="/about" element={protectedOnly(<About />)} />

      <Route path="/book/ride" element={protectedOnly(<RideBooking />)} />
      <Route path="/book/:kind" element={protectedOnly(<DeliveryBooking />)} />
      <Route path="/tracking/:bookingId" element={protectedOnly(<Tracking />)} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
