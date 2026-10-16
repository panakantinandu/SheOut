import { expect, test } from '@playwright/test';

/**
 * A mistake on the profile form is shown where it is and she is taken to it:
 * Continue with things missing scrolls to the first one and focuses it, every
 * problem is marked at its own field, and a line by the button says how many.
 */

test.describe.configure({ timeout: 120_000 });

const ACCOUNT = '00000000-0000-4000-8000-0000000000aa';
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=', 'base64');

test('Continue with things missing goes to the first one and marks each at its field', async ({ page }) => {
  let hasPhoto = false;
  await page.addInitScript((id) => {
    localStorage.setItem('sheout_language', 'en');
    localStorage.setItem('sheout_access_token', 'test-token');
    localStorage.setItem('sheout_account_id', id);
    sessionStorage.setItem('sheout_session_started', '1');
    sessionStorage.setItem('sheout_splash_shown', '1');
  }, ACCOUNT);
  await page.route('**/api/v1/**', (route) => {
    const req = route.request();
    const { pathname } = new URL(req.url());
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    const profile = () => ({ accountId: ACCOUNT, name: null, phoneNumber: '+919000000301', home: null, work: null, dateOfBirth: null, email: null,
      profilePhotoUrl: null, hasProfilePhoto: hasPhoto, onboardingSeen: false, profileComplete: false, verified: false, trustStats: null, updatedAt: new Date().toISOString() });
    if (pathname.endsWith('/users/customer/me/photo')) { hasPhoto = true; return json(profile()); }
    if (pathname.endsWith('/users/customer/me')) return json(profile());
    if (pathname.endsWith('/users/me/preferences')) return json({ language: 'en' });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
  await page.setViewportSize({ width: 390, height: 700 });
  await page.goto('/complete-profile', { timeout: 90_000 });
  const form = page.getByTestId('profile-completion-form');
  await expect(form).toBeVisible({ timeout: 60_000 });

  // Straight to Continue: everything is missing.
  await page.getByRole('button', { name: /^continue$/i }).click();
  await expect(page.getByTestId('profile-fix-above')).toContainText('4 things above need fixing');
  await expect(page.getByTestId('photo-error')).toBeVisible();
  await expect(page.getByTestId('profile-photo-block')).toBeInViewport();
  // Focus went to the first problem (the photo), not left on the button at the bottom.
  await expect.poll(() => page.evaluate(() => document.activeElement?.closest('[data-testid="profile-photo-block"]') !== null)).toBe(true);
  await expect(form.locator('input[name="name"]')).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByTestId('consent-error')).toBeVisible();

  // Fix all but the Terms box: Continue goes to the box, and says so there.
  await form.getByTestId('photo-input').setInputFiles({ name: 'me.png', mimeType: 'image/png', buffer: PNG });
  await form.locator('input[name="name"]').fill('Test Customer');
  await form.locator('input[name="dateOfBirth"]').fill('1995-06-15');
  await page.mouse.wheel(0, -2000);
  await page.getByRole('button', { name: /^continue$/i }).click();
  await expect(page.getByTestId('profile-fix-above')).toContainText('One thing above needs fixing');
  await expect(page.getByTestId('consent-row')).toBeInViewport();
  await expect(page.getByTestId('consent-error')).toContainText('Tick the box');
  await expect.poll(() => page.evaluate(() => (document.activeElement as HTMLElement | null)?.dataset.testid)).toBe('consent-checkbox');
});
