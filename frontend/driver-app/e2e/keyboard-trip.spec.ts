import { expect, test, type Page } from '@playwright/test';
import { mockPartner } from './support/mockPartner';

/**
 * The partner's day with the keyboard alone: sign in, go online, hear and
 * accept an offer, confirm the customer, start with her code and complete.
 * Focus is visible and never dropped to the page when a button is replaced,
 * and each change of state is read out.
 */

test.describe.configure({ timeout: 150_000 });

const active = (page: Page) =>
  page.evaluate(() => {
    const e = document.activeElement as HTMLElement | null;
    return {
      testid: e?.getAttribute('data-testid') ?? '',
      text: (e?.getAttribute('aria-label') || (e as HTMLInputElement | null)?.placeholder || e?.textContent || '').trim(),
      tag: e?.tagName ?? '',
      type: (e as HTMLInputElement | null)?.type ?? '',
      visible: e ? getComputedStyle(e).outlineStyle !== 'none' || getComputedStyle(e).boxShadow !== 'none' : false,
    };
  });

async function tabTo(page: Page, match: (a: Awaited<ReturnType<typeof active>>) => boolean, max = 30) {
  for (let i = 0; i < max; i++) {
    const a = await active(page);
    if (match(a)) return a;
    await page.keyboard.press('Tab');
  }
  throw new Error('not reachable with Tab');
}

test('partner sign-in works with the keyboard alone', async ({ page }) => {
  const partner = await mockPartner(page, 'en', false);
  await page.goto('/login', { timeout: 90_000 });
  const phone = await tabTo(page, (a) => a.type === 'tel');
  expect(phone.visible).toBe(true);
  await page.keyboard.type('9000000301');
  await tabTo(page, (a) => /send otp/i.test(a.text));
  await page.keyboard.press('Enter');
  await expect.poll(() => partner.state.otpRequests).toBe(1);
});

test('go online, accept an offer, confirm the customer, start and complete - by keyboard', async ({ page }) => {
  const partner = await mockPartner(page);
  await page.goto('/home', { timeout: 90_000 });
  await expect(page.getByTestId('go-online')).toBeVisible();

  // Go Online: focus moves to Go Offline (not to the page) and the change is read out.
  const goOnline = await tabTo(page, (a) => a.testid === 'go-online');
  expect(goOnline.visible).toBe(true);
  await page.keyboard.press('Enter');
  await expect(page.getByTestId('hero-announcement')).toHaveText(/online/i, { timeout: 20_000 });
  await expect.poll(async () => (await active(page)).testid, { timeout: 10_000 }).toBe('go-offline');

  // An offer: announced at once, focus on its details - never on Accept.
  partner.offer();
  await expect(page).toHaveURL(/\/offer\//, { timeout: 20_000 });
  await expect(page.getByTestId('offer-announcement')).toHaveText(/new request.*you earn.*pickup/i);
  await expect(page.getByTestId('offer-announcement')).toHaveAttribute('aria-live', 'assertive');
  expect((await active(page)).testid).toBe('offer-details');
  await tabTo(page, (a) => a.testid === 'offer-accept');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/trip\//, { timeout: 20_000 });
  await expect.poll(() => partner.state.accepted).toBe(1);

  // At the pickup: read out, then "is it her?", then her code.
  partner.arrive();
  await expect(page.getByTestId('trip-step-announcement')).toHaveText(/at the pickup/i, { timeout: 20_000 });
  await tabTo(page, (a) => a.testid === 'rider-check-yes');
  await page.keyboard.press('Enter');
  const code = await tabTo(page, (a) => a.tag === 'INPUT');
  expect(code.visible).toBe(true);
  await page.keyboard.type('4821');
  await tabTo(page, (a) => /start/i.test(a.text) && a.tag === 'BUTTON');
  await page.keyboard.press('Enter');
  await expect.poll(() => partner.state.started, { timeout: 15_000 }).toBe(1);
  await expect(page.getByTestId('trip-step-announcement')).toHaveText(/step 2 of 2/i, { timeout: 20_000 });

  // At the drop: end the trip.
  await page.context().setGeolocation({ latitude: 17.4504, longitude: 78.3808 });
  await expect(page.getByTestId('trip-step-announcement')).toHaveText(/reached the drop/i, { timeout: 30_000 });
  await tabTo(page, (a) => /end trip/i.test(a.text) && a.tag === 'BUTTON');
  await page.keyboard.press('Enter');
  await expect.poll(() => partner.state.completed, { timeout: 15_000 }).toBe(1);
});
