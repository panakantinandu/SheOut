import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

/**
 * Accessibility, checked by axe-core on the partner app's main screens: the
 * public pages and the signed-in ones against a mocked API. Every screen in
 * English, Hindi and Telugu at 360 px, and in English at all four widths. A
 * serious or critical violation fails the run.
 *
 *   npm run test:e2e -w frontend/driver-app -- a11y
 */

const ACCOUNT = '00000000-0000-4000-8000-000000000002';
const WIDTHS = [360, 375, 390, 430];
const LANGS = ['en', 'hi', 'te'] as const;

const PUBLIC = ['/login', '/terms', '/privacy', '/partner-terms', '/grievance', '/delete-account'];
const SIGNED_IN = ['/home', '/earnings', '/bookings', '/profile', '/settings', '/help', '/payouts'];

const now = new Date().toISOString();
const profile = {
  accountId: ACCOUNT, name: 'Lakshmi Devi', phoneNumber: '+919000000301', vehicleType: 'BIKE',
  vehicleRegistrationNumber: 'TS09AB1234', panNumber: null, onlineStatus: 'OFFLINE', verified: true,
  profilePhotoUrl: null, hasProfilePhoto: true, dateOfBirth: '1994-03-02', email: null, profileComplete: true, updatedAt: now,
};

async function prepare(page: Page, lang: string, signedIn: boolean) {
  await page.context().grantPermissions(['geolocation']);
  await page.context().setGeolocation({ latitude: 17.385, longitude: 78.4867 });
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
    { id: ACCOUNT, lang, signedIn }
  );
  await page.route('**/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    const empty = () => route.fulfill({ status: 204 });
    if (pathname.endsWith('/users/driver/me')) return json(profile);
    if (pathname.endsWith('/driver-verification/me')) {
      return json({ accountId: ACCOUNT, role: 'DRIVER', genderVerificationStatus: 'VERIFIED', policeVerificationStatus: 'VERIFIED',
        documentSubmitted: true, rejectionReason: null, updatedAt: now });
    }
    if (pathname.endsWith('/ratings/me')) return json({ averageStars: null, totalRatings: 0 });
    if (pathname.endsWith('/ratings/pending') || pathname.endsWith('/ratings/tags')) return json([]);
    if (pathname.endsWith('/bookings/me')) return json([]);
    if (pathname.endsWith('/bookings/me/payment-hold') || pathname.endsWith('/dispatch/offers/me')) return empty();
    if (pathname.endsWith('/unread-count')) return json({ unreadCount: 0 });
    if (pathname.endsWith('/users/me/preferences')) return json({ language: lang });
    if (pathname.endsWith('/notifications/push-config')) return json({ enabled: false, vapidKey: null, firebase: null });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
}

const cases: { path: string; lang: string; width: number; signedIn: boolean }[] = [];
for (const [paths, signedIn] of [[PUBLIC, false], [SIGNED_IN, true]] as const) {
  for (const path of paths) {
    for (const lang of LANGS) cases.push({ path, lang, width: 360, signedIn });
    for (const width of WIDTHS.slice(1)) cases.push({ path, lang: 'en', width, signedIn });
  }
}

for (const c of cases) {
  test(`${c.path} ${c.lang} ${c.width}px has no serious or critical violations`, async ({ page }) => {
    await page.setViewportSize({ width: c.width, height: 800 });
    await prepare(page, c.lang, c.signedIn);
    await page.goto(c.path, { timeout: 90_000 });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(600);
    // Scanning the sign-in screen by accident would pass and prove nothing.
    expect(new URL(page.url()).pathname).toBe(c.path);
    const { violations } = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
      .exclude('.gm-style')
      .analyze();
    for (const v of violations) {
      console.log(`[a11y] ${c.path} ${c.lang} ${c.width} ${v.impact} ${v.id} (${v.nodes.length}) ${v.nodes[0]?.target.join(' ')}`);
    }
    const blocking = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(blocking.map((v) => `${v.id}: ${v.help} -> ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
  });
}
