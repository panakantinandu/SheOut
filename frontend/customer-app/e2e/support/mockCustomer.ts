import type { Page } from '@playwright/test';

/**
 * A mocked backend for keyboard and screen-reader tests: one signed-in,
 * verified customer, address search (Nominatim, as without a Google key), a
 * fare quote, and a booking the test moves through its states with
 * `trip.set(...)`. Every other call answers 404, as an unknown route would.
 */
export const ACCOUNT = '00000000-0000-4000-8000-000000000001';
export const BOOKING = '00000000-0000-4000-8000-0000000000b1';

const PICKUP = { label: 'Road No. 12, Banjara Hills', lat: 17.4126, lng: 78.4402 };
const DROP = { label: 'Cyber Towers, HITEC City', lat: 17.4504, lng: 78.3808 };

export type TripStatus = 'REQUESTED' | 'MATCHED' | 'ACCEPTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export async function mockCustomer(page: Page, lang = 'en') {
  const state = { status: 'REQUESTED' as TripStatus, booked: false, cancelled: 0, category: 'BIKE' as string, sos: 0 };
  const now = () => new Date().toISOString();
  const booking = () => ({
    id: BOOKING, type: state.category === 'PARCEL' ? 'DELIVERY' : 'RIDE', category: state.category, status: state.status,
    customerId: ACCOUNT, driverId: ['REQUESTED'].includes(state.status) ? null : 'drv1',
    pickup: PICKUP, drop: DROP, fareEstimate: 142, finalFare: state.status === 'COMPLETED' ? 142 : null,
    requestedAt: now(), matchedAt: state.status === 'REQUESTED' ? null : now(),
    acceptedAt: ['ACCEPTED', 'IN_PROGRESS', 'COMPLETED'].includes(state.status) ? now() : null,
    startedAt: ['IN_PROGRESS', 'COMPLETED'].includes(state.status) ? now() : null,
    completedAt: state.status === 'COMPLETED' ? now() : null, cancelledAt: state.status === 'CANCELLED' ? now() : null,
    paymentSettledAt: null, promoDiscount: 0, amountDue: 142, promotionName: null,
    pickupCode: '4821',
    assignedDriver: ['ACCEPTED', 'IN_PROGRESS', 'COMPLETED'].includes(state.status)
      ? { name: 'Sample Partner', photoUrl: null, vehicleType: 'BIKE', vehicleRegistrationNumber: 'TS09AB1234', averageStars: null, totalRatings: 0 }
      : null,
  });

  await page.context().grantPermissions(['geolocation']);
  await page.context().setGeolocation({ latitude: PICKUP.lat, longitude: PICKUP.lng });
  await page.addInitScript(
    ({ id, lang }) => {
      localStorage.setItem('sheout_language', lang);
      localStorage.setItem('sheout_access_token', 'test-token');
      localStorage.setItem('sheout_account_id', id);
      localStorage.setItem(`sheout_gate_${id}`, 'open');
      sessionStorage.setItem('sheout_session_started', '1');
      sessionStorage.setItem('sheout_splash_shown', '1');
    },
    { id: ACCOUNT, lang }
  );

  // Address search without a Google key: OpenStreetMap's Nominatim.
  await page.route('https://nominatim.openstreetmap.org/**', (route) => {
    const url = new URL(route.request().url());
    const place = (p: typeof DROP) => ({ display_name: p.label, lat: String(p.lat), lon: String(p.lng), address: {} });
    const body = url.pathname.startsWith('/reverse') ? place(PICKUP) : [place(DROP)];
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.route('**/maps.googleapis.com/**', (route) => route.abort());

  await page.route('**/api/v1/**', async (route) => {
    const req = route.request();
    const { pathname } = new URL(req.url());
    const method = req.method();
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/users/customer/me')) {
      return json({
        accountId: ACCOUNT, name: 'Ananya', phoneNumber: '+919000000101', profileComplete: true, verified: true,
        onboardingSeen: true, hasProfilePhoto: false, profilePhotoUrl: null, home: null, work: null,
        trustStats: { totalBookings: 3, totalCancellations: 0, averageStars: null, totalRatings: 0, flagged: false },
      });
    }
    if (pathname.endsWith('/users/me/preferences')) return json({ language: lang });
    if (pathname.endsWith('/notifications/sos') && method === 'POST') {
      state.sos++;
      return json({ alertId: 'a1', contactsTotal: 1, contactsNotified: 1, contactsFailed: 0, contacts: [], success: true });
    }
    if (pathname.endsWith('/unread-count')) return json({ unreadCount: 0 });
    if (pathname.endsWith('/emergency-contacts')) return json([{ id: 'c1', name: 'Amma', phoneNumber: '+919000000201', relationship: 'Mother' }]);
    if (pathname.endsWith('/service-area')) return json({ centreLat: 17.385, centreLng: 78.4867, radiusKm: 100, centreName: 'Hyderabad' });
    if (pathname.endsWith('/service-status')) return json({ open: true, closedReason: null, pauseReason: null, reopensAt: null, closesAt: null, mode: 'ALWAYS_OPEN', opensAt: '06:30:00', closesAtLocal: '21:00:00', timeZone: 'Asia/Kolkata' });
    if (pathname.endsWith('/dispatch/search-config')) return json({ searchTimeoutSeconds: 120 });
    if (pathname.includes('/dispatch/nearby')) return json({ drivers: [{ lat: 17.414, lng: 78.441 }], radiusKm: 3 });
    if (pathname.endsWith('/bookings/quote')) {
      const body = req.postDataJSON() ?? {};
      state.category = body.category ?? state.category;
      return json({
        fareEstimate: 142, distanceKm: 9.1, durationMinutes: 18, routed: true, baseFare: 30, distanceCharge: 92, timeCharge: 20,
        surgeMultiplier: 1, nightMultiplier: 1, minimumFareApplied: false, category: state.category, promoDiscount: 0, youPay: 142,
        promotionName: null, pickupEtaMinutes: 4, dropBy: null,
      });
    }
    if (pathname.endsWith('/bookings/me/payment-hold')) return route.fulfill({ status: 204 });
    if (pathname.endsWith('/bookings') && method === 'POST') {
      const body = req.postDataJSON() ?? {};
      state.category = body.category ?? state.category;
      state.booked = true;
      state.status = 'REQUESTED';
      return json(booking(), 201);
    }
    if (pathname.endsWith('/bookings/me')) return json(state.booked ? [booking()] : []);
    if (pathname.endsWith(`/bookings/${BOOKING}/cancel`)) {
      state.cancelled++;
      state.status = 'CANCELLED';
      return json(booking());
    }
    if (pathname.endsWith(`/bookings/${BOOKING}`)) return json(booking());
    if (pathname.includes(`/dispatch/`) && pathname.includes('location')) {
      return json({ lat: 17.4135, lng: 78.4395, updatedAt: now(), etaMinutes: 3 });
    }
    if (pathname.endsWith('/parcels/rules')) {
      return json({ prohibitedItems: ['CASH', 'JEWELLERY', 'ALCOHOL'], prohibitedItemsVersion: '2026-10-11', maxWeightKg: 5, maxDeclaredValue: 5000 });
    }
    return json({ error: 'NOT_FOUND' }, 404);
  });

  return {
    state,
    set(status: TripStatus) {
      state.status = status;
    },
  };
}
