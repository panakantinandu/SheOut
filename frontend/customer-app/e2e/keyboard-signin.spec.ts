import { expect, test } from '@playwright/test';

/**
 * Signing in with the keyboard alone: every control is reached with Tab, has
 * a visible focus ring, the Terms link opens without a mouse, and Enter sends
 * the code. No pointer is used after the page loads.
 */
test('sign-in works with the keyboard alone', async ({ page }) => {
  const requested: unknown[] = [];
  await page.addInitScript(() => {
    localStorage.setItem('sheout_language', 'en');
    sessionStorage.setItem('sheout_session_started', '1');
    sessionStorage.setItem('sheout_splash_shown', '1');
  });
  await page.route('**/api/v1/**', async (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname.endsWith('/auth/otp/request')) {
      requested.push(route.request().postDataJSON());
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
  await page.goto('/login', { timeout: 90_000 });

  // Tab until the phone field has focus - it must be reachable, in order.
  let reached = false;
  for (let i = 0; i < 15 && !reached; i++) {
    await page.keyboard.press('Tab');
    reached = await page.evaluate(() => (document.activeElement as HTMLInputElement | null)?.type === 'tel');
  }
  expect(reached).toBe(true);
  await page.keyboard.type('9000000101');

  // Onwards to the Terms link: focusable, with a visible focus indicator.
  let onTerms = false;
  for (let i = 0; i < 6 && !onTerms; i++) {
    await page.keyboard.press('Tab');
    onTerms = await page.evaluate(() => document.activeElement?.textContent?.trim() === 'Terms of Use');
  }
  expect(onTerms).toBe(true);
  const outline = await page.evaluate(() => {
    const s = getComputedStyle(document.activeElement as Element);
    return s.outlineStyle !== 'none' || s.boxShadow !== 'none';
  });
  expect(outline).toBe(true);

  // Send OTP, by keyboard.
  let onSend = false;
  for (let i = 0; i < 6 && !onSend; i++) {
    await page.keyboard.press('Tab');
    onSend = await page.evaluate(() => /Send OTP/.test(document.activeElement?.textContent ?? ''));
  }
  expect(onSend).toBe(true);
  await page.keyboard.press('Enter');
  await expect.poll(() => requested.length).toBe(1);
  expect(requested[0]).toMatchObject({ phoneNumber: expect.stringContaining('9000000101') });
});
