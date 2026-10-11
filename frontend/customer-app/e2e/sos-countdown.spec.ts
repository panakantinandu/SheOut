import { expect, test, type Page } from '@playwright/test';

/**
 * The SOS button does not alert anyone on the tap: a 5-second countdown with
 * a large Cancel comes first (BUTTON_COUNTDOWN_SECONDS). These pin what each
 * way out of the countdown does, by counting the alerts the app sends.
 */

const ACCOUNT = '00000000-0000-4000-8000-000000000001';

async function openSos(page: Page) {
  const alerts: unknown[] = [];
  await page.context().grantPermissions(['geolocation']);
  await page.context().setGeolocation({ latitude: 17.4156, longitude: 78.4347 });
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
    if (pathname.endsWith('/notifications/sos') && route.request().method() === 'POST') {
      alerts.push(route.request().postDataJSON());
      return json({ alertId: 'a1', contactsTotal: 1, contactsNotified: 1, contactsFailed: 0, contacts: [], success: true });
    }
    if (pathname.endsWith('/emergency-contacts')) return json([{ id: 'c1', name: 'Amma', phoneNumber: '+919000000201', relationship: 'Mother' }]);
    if (pathname.endsWith('/users/customer/me')) {
      return json({
        accountId: ACCOUNT, name: 'Ananya', phoneNumber: '+919000000101', profileComplete: true, verified: true,
        onboardingSeen: true, hasProfilePhoto: false, profilePhotoUrl: null, home: null, work: null,
        trustStats: { totalBookings: 0, totalCancellations: 0, averageStars: null, totalRatings: 0, flagged: false },
      });
    }
    if (pathname.endsWith('/users/me/preferences')) return json({ language: 'en' });
    if (pathname.endsWith('/unread-count')) return json({ unreadCount: 0 });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
  // The first visit can wait on the dev server compiling the app.
  await page.goto('/sos', { timeout: 90_000 });
  await expect(page.getByTestId('sos-button')).toBeVisible();
  return alerts;
}

test('the SOS button starts a 5-second countdown, and Cancel alerts nobody', async ({ page }) => {
  const alerts = await openSos(page);
  await page.getByTestId('sos-button').click();
  await expect(page.getByTestId('sos-countdown')).toBeVisible();
  await expect(page.getByTestId('sos-countdown-seconds')).toHaveText('5');
  await page.getByTestId('sos-countdown-cancel').click();
  await expect(page.getByTestId('sos-countdown')).toBeHidden();
  // Past the end of the countdown, and then some: still nothing sent.
  await page.waitForTimeout(6_500);
  expect(alerts).toHaveLength(0);
});

test('Send SOS now sends at once, once', async ({ page }) => {
  const alerts = await openSos(page);
  await page.getByTestId('sos-button').click();
  await expect(page.getByTestId('sos-countdown-seconds')).toHaveText('5');
  await page.getByTestId('sos-countdown-send-now').click();
  await expect.poll(() => alerts.length, { timeout: 3_000 }).toBe(1);
  expect(alerts[0]).toMatchObject({ triggerSource: 'BUTTON' });
  await page.waitForTimeout(6_000);
  expect(alerts).toHaveLength(1);
});

test('with nothing pressed, the countdown sends the SOS when it ends - not before', async ({ page }) => {
  const alerts = await openSos(page);
  await page.getByTestId('sos-button').click();
  await page.waitForTimeout(4_000);
  expect(alerts).toHaveLength(0);
  await expect.poll(() => alerts.length, { timeout: 4_000 }).toBe(1);
  expect(alerts[0]).toMatchObject({ triggerSource: 'BUTTON' });
});
