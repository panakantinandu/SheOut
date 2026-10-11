import { expect, test, type Page } from '@playwright/test';

/**
 * Cancel on the trip screen: in view without scrolling on a 360 x 640
 * phone, at least 48 px tall, in the same row as Chat and Share for a bike
 * taxi and a parcel alike, never offered once the trip has started, and
 * never cancelling anything on the tap itself - a reason sheet comes first.
 */

const ACCOUNT = '00000000-0000-4000-8000-000000000001';
const TRIP = '00000000-0000-4000-8000-0000000000aa';

function booking(status: string, type: 'RIDE' | 'DELIVERY') {
  return {
    id: TRIP, type, category: type === 'RIDE' ? 'BIKE' : 'PARCEL', status, customerId: ACCOUNT,
    driverId: status === 'REQUESTED' ? null : '00000000-0000-4000-8000-000000000002',
    pickup: { label: 'Road No. 12, Banjara Hills', lat: 17.4156, lng: 78.4347 },
    drop: { label: 'Hitech City', lat: 17.4435, lng: 78.3772 },
    fareEstimate: 107, finalFare: null, requestedAt: new Date().toISOString(), matchedAt: null, acceptedAt: null,
    startedAt: null, completedAt: null, cancelledAt: null, paymentSettledAt: null, promoDiscount: 0, amountDue: 107,
    promotionName: null, parcelNote: type === 'DELIVERY' ? 'Two books' : null,
  };
}

async function openTrip(page: Page, status: string, type: 'RIDE' | 'DELIVERY') {
  const cancels: unknown[] = [];
  await page.setViewportSize({ width: 360, height: 640 });
  await page.addInitScript((id) => {
    localStorage.setItem('sheout_language', 'en');
    localStorage.setItem('sheout_access_token', 'test-token');
    localStorage.setItem('sheout_account_id', id);
    localStorage.setItem(`sheout_gate_${id}`, 'open');
    sessionStorage.setItem('sheout_session_started', '1');
  }, ACCOUNT);
  await page.route('**/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith(`/bookings/${TRIP}/cancel`)) {
      cancels.push(route.request().postDataJSON());
      return json({ ...booking('CANCELLED', type) });
    }
    if (pathname.endsWith(`/bookings/${TRIP}`)) return json(booking(status, type));
    if (pathname.endsWith(`/dispatch/bookings/${TRIP}/driver`)) {
      return json({ name: 'Lakshmi Devi', photoUrl: null, vehicleType: 'BIKE', vehicleRegistrationNumber: 'TS09EB5678', averageStars: 4.8, totalRatings: 12 });
    }
    if (pathname.endsWith('/users/me/preferences')) return json({ language: 'en' });
    if (pathname.endsWith('/unread-count')) return json({ unreadCount: 0 });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
  // The first visit can wait on the dev server compiling the app.
  await page.goto(`/tracking/${TRIP}`, { timeout: 90_000 });
  // Then the trip itself: on a cold server "Loading your trip" can outlast the default wait.
  await expect(page.getByTestId('trip-actions')).toBeVisible({ timeout: 60_000 });
  return cancels;
}

async function expectInViewAndTall(page: Page, testId: string) {
  const button = page.getByTestId(testId);
  await expect(button).toBeVisible();
  await expect(button).toBeInViewport({ ratio: 1 });
  const box = await button.boundingBox();
  expect(box!.height).toBeGreaterThanOrEqual(48);
}

for (const type of ['RIDE', 'DELIVERY'] as const) {
  for (const status of ['REQUESTED', 'ACCEPTED']) {
    test(`${type} ${status}: Cancel is in view at 360 x 640, and asks why before cancelling`, async ({ page }) => {
      const cancels = await openTrip(page, status, type);
      await expectInViewAndTall(page, 'cancel-ride');
      await expectInViewAndTall(page, 'share-trip');
      await expect(page.getByTestId('cancel-ride')).toHaveAccessibleName(type === 'RIDE' ? 'Cancel Ride' : 'Cancel delivery');

      await page.getByTestId('cancel-ride').click();
      await expect(page.getByText('Cancelling is free.', { exact: false })).toBeVisible();
      // A parcel's sender is never asked whether she found another ride.
      await expect(page.getByText('I found another ride')).toHaveCount(type === 'RIDE' ? 1 : 0);
      await page.getByRole('button', { name: 'Keep trip' }).click();
      expect(cancels).toHaveLength(0);
    });
  }

  test(`${type} IN_PROGRESS: no Cancel once the trip has started; Share is still there`, async ({ page }) => {
    await openTrip(page, 'IN_PROGRESS', type);
    await expect(page.getByTestId('trip-actions')).toBeVisible();
    await expect(page.getByTestId('share-trip')).toBeVisible();
    await expect(page.getByTestId('cancel-ride')).toHaveCount(0);
    await expect(page.getByTestId('tracking-sos')).toBeVisible();
  });
}

test('a parcel shows its own section with what is inside', async ({ page }) => {
  await openTrip(page, 'ACCEPTED', 'DELIVERY');
  await expect(page.getByTestId('parcel-section-note')).toHaveText('Two books');
});
