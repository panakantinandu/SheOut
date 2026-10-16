import { expect, test } from '@playwright/test';

/**
 * Opening Notifications is reading them, as on other apps: the server is told
 * at once (so the bell's count clears), and what was unread keeps its
 * highlight for this visit, counted as "new".
 */

test.describe.configure({ timeout: 120_000 });

const ACCOUNT = '00000000-0000-4000-8000-0000000000dd';

test('opening the inbox marks it read and still shows what is new', async ({ page }) => {
  let readAll = 0;
  let unread = 2;
  await page.addInitScript((id) => {
    localStorage.setItem('sheout_language', 'en');
    localStorage.setItem('sheout_access_token', 'test-token');
    localStorage.setItem('sheout_account_id', id);
    localStorage.setItem('sheout_gate_' + id, 'open');
    sessionStorage.setItem('sheout_session_started', '1');
    sessionStorage.setItem('sheout_splash_shown', '1');
  }, ACCOUNT);
  const item = (n: number, read: boolean) => ({ id: `n${n}`, type: 'TRIP_UPDATE', title: `Update ${n}`, body: 'Your trip has been updated.', link: null, read,
    createdAt: new Date(Date.now() - n * 60_000).toISOString() });
  await page.route('**/api/v1/**', (route) => {
    const req = route.request();
    const { pathname } = new URL(req.url());
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/notifications/me/read-all') && req.method() === 'POST') { readAll++; unread = 0; return route.fulfill({ status: 204 }); }
    if (pathname.endsWith('/notifications/me/unread-count')) return json({ unreadCount: unread });
    if (pathname.endsWith('/notifications/me')) {
      return json({ page: { items: [item(1, false), item(2, false), item(3, true)], page: 0, pageSize: 20, totalItems: 3, totalPages: 1, hasMore: false }, unreadCount: unread });
    }
    if (pathname.endsWith('/users/customer/me')) {
      return json({ accountId: ACCOUNT, name: 'Test Customer', phoneNumber: '+919000000301', home: null, work: null, dateOfBirth: '1995-06-15', email: null,
        profilePhotoUrl: null, hasProfilePhoto: true, onboardingSeen: true, profileComplete: true, verified: true, trustStats: null, updatedAt: new Date().toISOString() });
    }
    if (pathname.endsWith('/users/me/preferences')) return json({ language: 'en' });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
  await page.goto('/notifications', { timeout: 90_000 });
  await expect(page.getByTestId('inbox-item').first()).toBeVisible({ timeout: 60_000 });
  await expect.poll(() => readAll).toBe(1);
  await expect(page.getByTestId('inbox-new')).toHaveText('2 new');
  await expect(page.locator('[data-testid="inbox-item"][data-read="false"]')).toHaveCount(2);
  await expect(page.getByRole('button', { name: /mark all read/i })).toHaveCount(0);
});
