import { expect, test, type Page } from '@playwright/test';

/**
 * Money on the Payouts screen: a whole balance reads "₹3,413", never
 * "₹3,413.00", and a balance with paise keeps them exactly ("₹3,412.68") -
 * a payout must not be shown rounded.
 */

test.describe.configure({ timeout: 120_000 });

const ACCOUNT = '00000000-0000-4000-8000-000000000002';
const now = new Date().toISOString();

async function open(page: Page, wallet: { available: number; earned: number; cash: number; paidOut: number }) {
  await page.addInitScript((id) => {
    localStorage.setItem('sheout_language', 'en');
    sessionStorage.setItem('sheout_session_started', '1');
    sessionStorage.setItem('sheout_splash_shown', '1');
    localStorage.setItem('sheout_driver_access_token', 'test-token');
    localStorage.setItem('sheout_driver_account_id', id);
    localStorage.setItem('sheout_driver_push_token.dismissedAt', String(Date.now()));
  }, ACCOUNT);
  await page.route('**/api/v1/**', (route) => {
    const { pathname } = new URL(route.request().url());
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/payouts/me')) {
      return json({
        wallet: { totalEarned: wallet.earned, cashCollected: wallet.cash, totalPaidOut: wallet.paidOut, pendingPayouts: 0, availableBalance: wallet.available },
        account: { accountHolderName: 'Sample Partner', accountNumberMasked: 'XXXXXXXX9012', ifsc: 'HDFC0001234', upiVpa: null, updatedAt: now },
        requests: [{ id: 'r1', amount: 500, status: 'PAID', destination: 'Bank a/c ending 9012', requestedAt: now, paidAt: now, paymentReference: 'UTR1' }],
      });
    }
    if (pathname.endsWith('/users/driver/me')) {
      return json({ accountId: ACCOUNT, name: 'Sample Partner', phoneNumber: '+919000000301', vehicleType: 'BIKE', vehicleRegistrationNumber: 'TS09AB1234',
        panNumber: null, onlineStatus: 'OFFLINE', verified: true, profilePhotoUrl: null, hasProfilePhoto: true, dateOfBirth: '1994-03-02', email: null,
        profileComplete: true, updatedAt: now });
    }
    if (pathname.endsWith('/users/me/preferences')) return json({ language: 'en' });
    if (pathname.endsWith('/unread-count')) return json({ unreadCount: 0 });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
  await page.goto('/payouts', { timeout: 90_000 });
  await expect(page.getByText('Available balance', { exact: false }).first()).toBeVisible({ timeout: 30_000 });
}

test('a whole balance has no ".00", and the amount field starts whole', async ({ page }) => {
  await open(page, { available: 3413, earned: 4976, cash: 1358, paidOut: 205 });
  const body = await page.locator('body').innerText();
  expect(body).toContain('₹3,413');
  expect(body).toContain('₹4,976');
  expect(body).toContain('₹500');
  expect(body).not.toMatch(/₹[\d,]+\.00\b/);
  await expect(page.locator('input[inputmode="decimal"], input[type="number"], input[type="text"]').last()).toHaveValue('3413');
});

test('a balance with paise keeps them exactly', async ({ page }) => {
  await open(page, { available: 3412.68, earned: 4976.36, cash: 1358.68, paidOut: 205 });
  const body = await page.locator('body').innerText();
  expect(body).toContain('₹3,412.68');
  expect(body).toContain('₹4,976.36');
  expect(body).toContain('₹205');
  expect(body).not.toMatch(/₹[\d,]+\.00\b/);
});
