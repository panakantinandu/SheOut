import { expect, test } from '@playwright/test';
import { officerInEffect } from '../../design-system/src/legal/officers';

/**
 * Grievance officers come from legal-entity.config.json: one list of
 * appointments per regime, each with the date it takes effect. Changing an
 * officer is one config edit; a page shows whoever is in effect that day.
 */

const appointment = (name: string, effectiveFrom: string) => ({
  name, designation: 'Grievance Officer', email: 'grievance@example.com', basedIn: 'India', effectiveFrom,
});

test('the appointment in effect on the day is the one shown', () => {
  const list = [appointment('First Officer', '2026-11-01'), appointment('Second Officer', '2027-03-01')];
  const on = (day: string) => officerInEffect(list, new Date(`${day}T12:00:00Z`)).name;
  expect(on('2026-10-31')).toBe('TODO_FROM_OWNER'); // nobody named before the first starts
  expect(on('2026-11-01')).toBe('First Officer');
  expect(on('2027-02-28')).toBe('First Officer');
  expect(on('2027-03-01')).toBe('Second Officer'); // the change is one added appointment
});

test('an undated placeholder stands until a dated appointment starts', () => {
  const list = [appointment('TODO_FROM_OWNER', 'TODO_FROM_OWNER'), appointment('Named Officer', '2027-01-01')];
  expect(officerInEffect(list, new Date('2026-12-31T12:00:00Z')).name).toBe('TODO_FROM_OWNER');
  expect(officerInEffect(list, new Date('2027-01-01T12:00:00Z')).name).toBe('Named Officer');
});

test('the Grievance page names an officer for every regime, with visible placeholders until supplied', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('sheout_language', 'en'));
  await page.goto('/grievance', { timeout: 90_000 });
  const body = page.locator('body');
  await expect(body).toContainText('complaints about a trip, a delivery, a payment or a refund');
  await expect(body).toContainText('questions and complaints about your personal data');
  await expect(body).toContainText('complaints about a marketplace listing or other content on SheOut');
  await expect(body).toContainText('Consumer Protection (E-Commerce) Rules, 2020, rule 4');
  await expect(body).toContainText('[to be completed by SheOut]');
  await expect(body).not.toContainText('TODO_FROM_OWNER');
});
