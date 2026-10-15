import { expect, test, type Page } from '@playwright/test';
import { mockCustomer } from './support/mockCustomer';

/**
 * Booking a ride and following it with the keyboard alone - no mouse after
 * the page loads - and what a screen reader is told as the trip moves on.
 * Focus must be visible, move in a sensible order, stay inside an open
 * dialog, leave it with Escape, and come back to what opened it.
 */

test.describe.configure({ timeout: 120_000 });

const active = (page: Page) =>
  page.evaluate(() => {
    const e = document.activeElement as HTMLElement | null;
    return {
      testid: e?.getAttribute('data-testid') ?? '',
      text: (e?.getAttribute('aria-label') || (e as HTMLInputElement | null)?.placeholder || e?.textContent || '').trim(),
      tag: e?.tagName ?? '',
      // A visible focus indicator: an outline or a ring (box-shadow).
      visible: e ? getComputedStyle(e).outlineStyle !== 'none' || getComputedStyle(e).boxShadow !== 'none' : false,
    };
  });

/** Tab until `match` has focus; fails if it is never reached (not keyboard-reachable). */
async function tabTo(page: Page, match: (a: Awaited<ReturnType<typeof active>>) => boolean, max = 25) {
  for (let i = 0; i < max; i++) {
    const a = await active(page);
    if (match(a)) return a;
    await page.keyboard.press('Tab');
  }
  throw new Error('not reachable with Tab');
}

test('book a ride, follow it and open the cancel dialog, all by keyboard', async ({ page }) => {
  const trip = await mockCustomer(page);
  await page.goto('/book/ride', { timeout: 90_000 });
  await expect(page.getByTestId('booking-map-layout')).toBeVisible();

  // The drop field, then the address picker: focus goes into it.
  const drop = await tabTo(page, (a) => a.text.startsWith('Drop'));
  expect(drop.visible).toBe(true);
  await page.keyboard.press('Enter');
  const dialog = page.getByTestId('location-picker');
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveAttribute('role', 'dialog');
  await expect(dialog).toHaveAttribute('aria-modal', 'true');
  expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true);

  // Tab stays inside the open picker.
  await page.keyboard.type('Cyber');
  await expect(page.getByRole('button', { name: 'Cyber Towers, HITEC City' })).toBeVisible();
  for (let i = 0; i < 8; i++) {
    await page.keyboard.press('Tab');
    expect(await dialog.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  }
  await tabTo(page, (a) => a.text === 'Cyber Towers, HITEC City');
  await page.keyboard.press('Enter');
  await expect(dialog).toBeHidden();
  // Back on the field that opened it.
  expect((await active(page)).text).toContain('Drop');

  // Book Now.
  await tabTo(page, (a) => a.testid === 'book-now');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/tracking\//);

  // What a screen reader hears as the trip moves on.
  const announcement = page.getByTestId('trip-announcement');
  await expect(announcement).toHaveText(/searching for a nearby partner/i);
  trip.set('ACCEPTED');
  await expect(announcement).toHaveText(/arriving|on her way/i, { timeout: 20_000 });
  trip.set('IN_PROGRESS');
  await expect(announcement).toHaveText(/on the way to your drop/i, { timeout: 20_000 });

  // Back to before the trip started, to reach Cancel.
  trip.set('ACCEPTED');
  await expect(announcement).toHaveText(/arriving|on her way/i, { timeout: 20_000 });
  const cancel = await tabTo(page, (a) => /cancel/i.test(a.text) && a.tag === 'BUTTON');
  expect(cancel.visible).toBe(true);
  await page.keyboard.press('Enter');
  const confirm = page.getByRole('dialog');
  await expect(confirm).toBeVisible();
  expect(await confirm.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press('Tab');
    expect(await confirm.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(confirm).toBeHidden();
  expect((await active(page)).text).toMatch(/cancel/i);
  expect(trip.state.cancelled).toBe(0);
});

test('SOS by keyboard: the countdown takes focus, is announced, Escape cancels, Send now sends once', async ({ page }) => {
  const trip = await mockCustomer(page);
  await page.goto('/sos', { timeout: 90_000 });
  await expect(page.getByTestId('sos-button')).toBeVisible();

  const sos = await tabTo(page, (a) => a.testid === 'sos-button', 40);
  expect(sos.visible).toBe(true);
  await page.keyboard.press('Enter');
  const countdown = page.getByTestId('sos-countdown');
  await expect(countdown).toBeVisible();
  // Focus on Cancel, the safe choice; the seconds are announced as they change.
  expect((await active(page)).testid).toBe('sos-countdown-cancel');
  await expect(page.getByTestId('sos-countdown-seconds')).toHaveAttribute('aria-live', 'assertive');
  for (let i = 0; i < 4; i++) {
    await page.keyboard.press('Tab');
    expect(await countdown.evaluate((d) => d.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(countdown).toBeHidden();
  expect((await active(page)).testid).toBe('sos-button');
  await page.waitForTimeout(6_000);
  expect(trip.state.sos).toBe(0);

  await page.keyboard.press('Enter');
  await expect(countdown).toBeVisible();
  await tabTo(page, (a) => a.testid === 'sos-countdown-send-now', 5);
  await page.keyboard.press('Enter');
  await expect.poll(() => trip.state.sos, { timeout: 5_000 }).toBe(1);
});

test('send a parcel by keyboard: the details sheet keeps focus and books once both boxes are ticked', async ({ page }) => {
  const trip = await mockCustomer(page);
  await page.goto('/book/parcel', { timeout: 90_000 });
  await expect(page.getByTestId('booking-map-layout')).toBeVisible();

  await tabTo(page, (a) => a.text.startsWith('Drop'));
  await page.keyboard.press('Enter');
  await page.keyboard.type('Cyber');
  await expect(page.getByRole('button', { name: 'Cyber Towers, HITEC City' })).toBeVisible();
  await tabTo(page, (a) => a.text === 'Cyber Towers, HITEC City');
  await page.keyboard.press('Enter');
  await tabTo(page, (a) => a.testid === 'book-now');
  await page.keyboard.press('Enter');

  const sheet = page.getByRole('dialog', { name: /parcel details/i });
  await expect(sheet).toBeVisible();
  expect((await active(page)).testid).toBe('receiver-name');
  await page.keyboard.type('Priya');
  await page.keyboard.press('Tab');
  await page.keyboard.type('9000000301');
  await tabTo(page, (a) => a.testid === 'size-ack', 6);
  await page.keyboard.press('Space');
  await tabTo(page, (a) => a.testid === 'prohibited-ack', 6);
  await page.keyboard.press('Space');
  // The book button is now in the sheet's Tab order, and Tab never leaves the sheet.
  for (let i = 0; i < 8; i++) {
    await page.keyboard.press('Tab');
    expect(await sheet.evaluate((d) => d.contains(document.activeElement))).toBe(true);
    if (/book/i.test((await active(page)).text)) break;
  }
  expect((await active(page)).text).toMatch(/book/i);
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/tracking\//);
  expect(trip.state.category).toBe('PARCEL');
});
