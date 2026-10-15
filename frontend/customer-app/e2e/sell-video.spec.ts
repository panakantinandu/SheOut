import { expect, test } from '@playwright/test';

/** "Watch how it works" plays the walkthrough captioned in her language, and the file is there. */

test.describe.configure({ timeout: 120_000 });

for (const lang of ['en', 'hi', 'te']) {
  test(`the seller walkthrough is in ${lang}`, async ({ page, request }) => {
    await page.addInitScript((l) => {
      localStorage.setItem('sheout_language', l);
      localStorage.setItem('sheout_access_token', 'test-token');
      localStorage.setItem('sheout_account_id', '00000000-0000-4000-8000-000000000001');
      sessionStorage.setItem('sheout_session_started', '1');
      sessionStorage.setItem('sheout_splash_shown', '1');
    }, lang);
    await page.route('**/api/v1/**', (route) => {
      const { pathname } = new URL(route.request().url());
      const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
      if (pathname.includes('/marketplace/listings')) return json({ items: [], page: 0, pageSize: 20, totalItems: 0, totalPages: 0 });
      if (pathname.endsWith('/users/me/preferences')) return json({ language: lang });
      return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
    });
    await page.goto('/seller', { timeout: 90_000 });
    const open = page.getByTestId('market-sell-video');
    await open.scrollIntoViewIfNeeded();
    await expect(open.locator('img')).toHaveAttribute('src', `/videos/sell-on-sheout-poster.${lang}.webp`);
    await open.click();
    const video = page.locator('video');
    await expect(video).toHaveAttribute('src', `/videos/sell-on-sheout.${lang}.mp4`);
    const file = await request.get(`/videos/sell-on-sheout.${lang}.mp4`);
    expect(file.ok()).toBe(true);
    expect((await file.body()).length).toBeGreaterThan(500_000);
  });
}
