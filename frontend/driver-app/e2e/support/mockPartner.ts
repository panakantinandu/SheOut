import type { Page } from '@playwright/test';

/**
 * A mocked backend for keyboard tests of the partner app: a verified, ready
 * partner who can go online, receive one offer (`partner.offer()`), accept it,
 * start it with the pickup code and complete it. Every other call answers 404.
 */
export const PARTNER = '00000000-0000-4000-8000-000000000002';
export const BOOKING = '00000000-0000-4000-8000-0000000000c1';

const PICKUP = { label: 'Road No. 12, Banjara Hills', lat: 17.4126, lng: 78.4402 };
const DROP = { label: 'Cyber Towers, HITEC City', lat: 17.4504, lng: 78.3808 };

export async function mockPartner(page: Page, lang = 'en', signedIn = true) {
  const state = {
    online: false,
    offered: false,
    status: null as null | 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED',
    accepted: 0,
    started: 0,
    completed: 0,
    otpRequests: 0,
    atPickup: false,
  };
  const now = () => new Date().toISOString();
  const profile = () => ({
    accountId: PARTNER, name: 'Sample Partner', phoneNumber: '+919000000301', vehicleType: 'BIKE',
    vehicleRegistrationNumber: 'TS09AB1234', panNumber: null, onlineStatus: state.online ? 'ONLINE' : 'OFFLINE', verified: true,
    profilePhotoUrl: null, hasProfilePhoto: true, dateOfBirth: '1994-03-02', email: null, profileComplete: true, updatedAt: now(),
  });
  const booking = () => ({
    id: BOOKING, type: 'RIDE', category: 'BIKE', status: state.status ?? 'MATCHED', customerId: 'cust1', driverId: PARTNER,
    pickup: PICKUP, drop: DROP, fareEstimate: 142, finalFare: state.status === 'COMPLETED' ? 142 : null,
    requestedAt: now(), matchedAt: now(), acceptedAt: state.status ? now() : null,
    startedAt: state.status === 'IN_PROGRESS' || state.status === 'COMPLETED' ? now() : null,
    completedAt: state.status === 'COMPLETED' ? now() : null, cancelledAt: null, paymentSettledAt: null,
    promoDiscount: 0, amountDue: 142, promotionName: null, partnerEarnings: 116,
  });

  await page.context().grantPermissions(['geolocation']);
  await page.context().setGeolocation({ latitude: 17.4135, longitude: 78.4395 });
  await page.addInitScript(
    ({ id, lang, signedIn }) => {
      localStorage.setItem('sheout_language', lang);
      sessionStorage.setItem('sheout_session_started', '1');
      sessionStorage.setItem('sheout_splash_shown', '1');
      if (signedIn) {
        localStorage.setItem('sheout_driver_access_token', 'test-token');
        localStorage.setItem('sheout_driver_account_id', id);
        localStorage.setItem('sheout_driver_push_token.dismissedAt', String(Date.now()));
      }
    },
    { id: PARTNER, lang, signedIn }
  );
  await page.route('**/maps.googleapis.com/**', (route) => route.abort());
  await page.route('https://nominatim.openstreetmap.org/**', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ display_name: PICKUP.label, lat: '17.41', lon: '78.44', address: {} }) })
  );

  await page.route('**/api/v1/**', async (route) => {
    const req = route.request();
    const { pathname } = new URL(req.url());
    const method = req.method();
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    const empty = () => route.fulfill({ status: 204 });
    if (pathname.endsWith('/auth/otp/request')) {
      state.otpRequests++;
      return empty();
    }
    if (pathname.endsWith('/users/driver/me') && method === 'GET') return json(profile());
    if (pathname.endsWith('/users/driver/me/status')) {
      const body = req.postDataJSON() ?? {};
      state.online = (body.status ?? body.onlineStatus) === 'ONLINE';
      return json(profile());
    }
    if (pathname.endsWith('/users/driver/me/readiness')) return json({ ready: true, blockers: [], warnings: [], documents: [] });
    if (pathname.endsWith('/driver-verification/shift-check')) {
      return json({ required: false, valid: true, underReview: false, checkedAt: now(), validUntil: null, faceMatched: true,
        helmetPhotoOnFile: true, lastResult: 'MATCH', missesBeforeReview: 3, reviewTargetMinutes: 30 });
    }
    if (pathname.endsWith('/driver-verification/me')) {
      return json({ accountId: PARTNER, role: 'DRIVER', genderVerificationStatus: 'VERIFIED', policeVerificationStatus: 'VERIFIED',
        documentSubmitted: true, rejectionReason: null, updatedAt: now() });
    }
    if (pathname.endsWith('/service-area')) return json({ centreLat: 17.385, centreLng: 78.4867, radiusKm: 100, centreName: 'Hyderabad' });
    if (pathname.endsWith('/service-status')) {
      return json({ open: true, closedReason: null, pauseReason: null, reopensAt: null, closesAt: null, mode: 'ALWAYS_OPEN',
        opensAt: '06:30:00', closesAtLocal: '21:00:00', timeZone: 'Asia/Kolkata' });
    }
    if (pathname.endsWith('/ratings/me')) return json({ averageStars: null, totalRatings: 0 });
    if (pathname.endsWith('/ratings/pending') || pathname.endsWith('/ratings/tags')) return json([]);
    if (pathname.endsWith('/users/me/preferences')) return json({ language: lang });
    if (pathname.endsWith('/unread-count')) return json({ unreadCount: 0 });
    if (pathname.endsWith('/notifications/push-config')) return json({ enabled: false, vapidKey: null, firebase: null });
    if (pathname.endsWith('/dispatch/location')) return empty();
    if (pathname.endsWith('/dispatch/offers/me')) {
      return state.offered && !state.status
        ? json({ bookingId: BOOKING, pickup: PICKUP, drop: DROP, fareEstimate: 142, category: 'BIKE', partnerEarnings: 116, commissionPercent: 18 })
        : empty();
    }
    // Accepting is two calls in the app: claim the offer, then accept the booking. Counted once.
    if (pathname.endsWith(`/dispatch/offers/${BOOKING}/accept`)) {
      state.accepted++;
      state.status = 'ACCEPTED';
      return json(booking());
    }
    if (pathname.endsWith(`/bookings/${BOOKING}/accept`)) {
      state.status = 'ACCEPTED';
      return json(booking());
    }
    if (pathname.endsWith(`/bookings/${BOOKING}/start`)) {
      state.started++;
      state.status = 'IN_PROGRESS';
      return json(booking());
    }
    if (pathname.endsWith(`/bookings/${BOOKING}/complete`)) {
      state.completed++;
      state.status = 'COMPLETED';
      return json(booking());
    }
    if (pathname.endsWith(`/bookings/${BOOKING}/pickup-status`)) return json({ arrived: state.atPickup });
    if (pathname.endsWith('/bookings/me/payment-hold')) return empty();
    if (pathname.includes('/bookings/me')) return json(state.status ? [booking()] : []);
    if (pathname.endsWith(`/bookings/${BOOKING}`)) return json(booking());
    if (pathname.endsWith('/payouts/me/earnings')) return json({ today: 0, week: 0, month: 0, trips: 0 });
    return json({ error: 'NOT_FOUND' }, 404);
  });

  return {
    state,
    offer() {
      state.offered = true;
    },
    /** The server agrees she is at the pickup. */
    arrive() {
      state.atPickup = true;
    },
  };
}
