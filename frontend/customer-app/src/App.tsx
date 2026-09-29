import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AppShell } from './layout/AppShell'
import { PageShell } from './layout/PageShell'
import { DeliveryBooking } from './screens/DeliveryBooking'
import { Home } from './screens/Home'
import { About } from './screens/About'
import { HelpSupport } from './screens/HelpSupport'
import { RaiseIssue } from './screens/RaiseIssue'
import { SupportTicket } from './screens/SupportTicket'
import { Login } from './screens/Login'
import { EmergencyContacts } from './screens/EmergencyContacts'
import { Privacy } from './screens/Privacy'
import { Terms } from './screens/Terms'
import { Notifications } from './screens/Notifications'
import { PaymentMethods } from './screens/PaymentMethods'
import { Devices } from './screens/Devices'
import { Onboarding } from './screens/Onboarding'
import { PersonalDetails } from './screens/PersonalDetails'
import { SavedAddresses } from './screens/SavedAddresses'
import { Verification } from './screens/Verification'
import { MyBookings } from './screens/MyBookings'
import { Profile } from './screens/Profile'
import { RideBooking } from './screens/RideBooking'
import { Sos } from './screens/Sos'
import { Splash } from './screens/Splash'
import { Tracking } from './screens/Tracking'
import { Chat } from './screens/Chat'
import { CompleteProfile } from './screens/CompleteProfile'
import { AddPhone } from './screens/AddPhone'
import { Wallet } from './screens/Wallet'
import { Seller } from './screens/Seller'
import { SellerProduct } from './screens/SellerProduct'
import { SellerShop } from './screens/SellerShop'
import { SellerProductEditor } from './screens/SellerProductEditor'
import { Refer } from './screens/Refer'
import { AppDrawerProvider } from './components/AppDrawer'
import { DiscreetSosGuard } from './components/DiscreetSosGuard'
import { SafetyCenter } from './screens/SafetyCenter'
import { HelpAssistant } from './screens/HelpAssistant'

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
  return (
    <ProtectedRoute>
      <PageShell>{element}</PageShell>
    </ProtectedRoute>
  )
}

/**
 * The map is the screen: no PageShell, whose page transition leaves a
 * transform behind that would pin a position:fixed layout to it.
 */
function fullScreen(element: JSX.Element) {
  return <ProtectedRoute>{element}</ProtectedRoute>
}

/** Readable without an account - the legal documents are linked from sign-in. */
function publicPage(element: JSX.Element) {
  return <PageShell>{element}</PageShell>
}

function App() {
  return (
    <AppDrawerProvider>
    <DiscreetSosGuard>
    <Routes>
      <Route path="/" element={<Splash />} />
      <Route path="/login" element={<Login />} />
      <Route path="/privacy" element={publicPage(<Privacy />)} />
      <Route path="/terms" element={publicPage(<Terms />)} />

      <Route path="/home" element={shell(<Home />)} />
      <Route path="/bookings" element={shell(<MyBookings />)} />
      <Route path="/wallet" element={shell(<Wallet />)} />
      <Route path="/profile" element={shell(<Profile />)} />
      <Route path="/sos" element={shell(<Sos />)} />

      {/* Pushed on top of Profile with a back arrow, like the booking flow -
          they are drill-downs, not tab destinations, so no AppShell. */}
      <Route path="/notifications" element={protectedOnly(<Notifications />)} />
      <Route path="/add-phone" element={protectedOnly(<AddPhone />)} />
      <Route path="/complete-profile" element={protectedOnly(<CompleteProfile />)} />
      <Route path="/welcome" element={protectedOnly(<Onboarding />)} />
      <Route path="/profile/details" element={protectedOnly(<PersonalDetails />)} />
      <Route path="/profile/addresses" element={protectedOnly(<SavedAddresses />)} />
      <Route path="/profile/payments" element={protectedOnly(<PaymentMethods />)} />
      <Route path="/profile/devices" element={protectedOnly(<Devices />)} />
      <Route path="/profile/emergency-contacts" element={protectedOnly(<EmergencyContacts />)} />
      <Route path="/verification" element={protectedOnly(<Verification />)} />
      <Route path="/help" element={protectedOnly(<HelpSupport />)} />
      <Route path="/help/new" element={protectedOnly(<RaiseIssue />)} />
      <Route path="/help/tickets/:ticketId" element={protectedOnly(<SupportTicket />)} />
      <Route path="/about" element={protectedOnly(<About />)} />

      <Route path="/book/ride" element={fullScreen(<RideBooking />)} />
      <Route path="/book/:kind" element={fullScreen(<DeliveryBooking />)} />
      <Route path="/tracking/:bookingId" element={protectedOnly(<Tracking />)} />
      <Route path="/chat/:bookingId" element={protectedOnly(<Chat />)} />

      {/* The directory is reached from Home's SheOut Seller tile and pushed
          on top like the booking flow. Same URL as when it was a tab, so
          the waitlist announcements that link to /seller still land here. */}
      <Route path="/seller" element={protectedOnly(<Seller />)} />
      <Route path="/seller/products/:productId" element={protectedOnly(<SellerProduct />)} />
      <Route path="/seller/manage" element={protectedOnly(<SellerShop />)} />
      <Route path="/seller/manage/products/:productId" element={protectedOnly(<SellerProductEditor />)} />
      <Route path="/refer" element={protectedOnly(<Refer />)} />

      <Route path="/safety" element={protectedOnly(<SafetyCenter />)} />
      <Route path="/help/assistant" element={protectedOnly(<HelpAssistant />)} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </DiscreetSosGuard>
    </AppDrawerProvider>
  )
}

export default App
