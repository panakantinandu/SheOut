import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

/**
 * Accessibility, checked by axe-core on every main screen: the public pages
 * (sign-in, the legal pages, Delete your account) and the signed-in ones
 * against a mocked API. Every screen in English, Hindi and Telugu at 360 px,
 * and in English at all four widths we design for. A serious or critical
 * violation fails the run; the rest are printed for the report.
 *
 *   npm run test:e2e -w frontend/customer-app -- a11y
 */

const ACCOUNT = '00000000-0000-4000-8000-000000000001';
// The first cases wait on the dev server compiling the app; 60 s is not always enough on a cold start.
test.describe.configure({ timeout: 120_000 });

const WIDTHS = [360, 375, 390, 430];
const LANGS = ['en', 'hi', 'te'] as const;

const PUBLIC = ['/login', '/terms', '/privacy', '/refunds', '/storage-policy', '/grievance', '/contact', '/delete-account'];
const SIGNED_IN = ['/home', '/sos', '/settings', '/profile', '/help', '/bookings', '/wallet', '/profile/emergency-contacts'];

async function prepare(page: Page, lang: string, signedIn: boolean) {
  await page.context().grantPermissions(['geolocation']);
  await page.context().setGeolocation({ latitude: 17.4156, longitude: 78.4347 });
  await page.addInitScript(
    ({ id, lang, signedIn }) => {
      localStorage.setItem('sheout_language', lang);
      sessionStorage.setItem('sheout_session_started', '1');
      sessionStorage.setItem('sheout_splash_shown', '1');
      if (signedIn) {
        localStorage.setItem('sheout_access_token', 'test-token');
        localStorage.setItem('sheout_account_id', id);
        localStorage.setItem(`sheout_gate_${id}`, 'open');
      }
    },
    { id: ACCOUNT, lang, signedIn }
  );
  await page.route('**/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/users/customer/me')) {
      return json({
        accountId: ACCOUNT, name: 'Ananya', phoneNumber: '+919000000101', profileComplete: true, verified: true,
        onboardingSeen: true, hasProfilePhoto: false, profilePhotoUrl: null, home: null, work: null,
        trustStats: { totalBookings: 0, totalCancellations: 0, averageStars: null, totalRatings: 0, flagged: false },
      });
    }
    if (pathname.endsWith('/users/me/preferences')) return json({ language: lang });
    if (pathname.endsWith('/unread-count')) return json({ unreadCount: 0 });
    if (pathname.endsWith('/emergency-contacts')) return json([{ id: 'c1', name: 'Amma', phoneNumber: '+919000000201', relationship: 'Mother' }]);
    if (pathname.endsWith('/bookings/me')) return json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
}

async function scan(page: Page, path: string) {
  // The first visit can wait on the dev server compiling the app.
  await page.goto(path, { timeout: 90_000 });
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(600);
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    // The Google map draws its own controls; they are Google's to fix.
    .exclude('.gm-style')
    .analyze();
  return result.violations;
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
    const violations = await scan(page, c.path);
    // Scanning the sign-in screen by accident would pass and prove nothing.
    expect(new URL(page.url()).pathname).toBe(c.path);
    for (const v of violations) {
      console.log(`[a11y] ${c.path} ${c.lang} ${c.width} ${v.impact} ${v.id} (${v.nodes.length}) ${v.nodes[0]?.target.join(' ')}`);
    }
    const blocking = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
    expect(blocking.map((v) => `${v.id}: ${v.help} -> ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
  });
}
