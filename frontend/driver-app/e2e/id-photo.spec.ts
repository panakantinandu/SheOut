import { expect, test, type Page } from '@playwright/test';
import { fileURLToPath } from 'url';

/**
 * Her ID photo: taken live with the camera, checked on the phone, and when the
 * check has doubts "Use anyway" really does carry on - to covering the
 * Aadhaar number, then sending.
 */

test.describe.configure({ timeout: 120_000 });

const ACCOUNT = '00000000-0000-4000-8000-0000000000cc';
const now = new Date().toISOString();
const DARK = fileURLToPath(new URL('./fixtures/dark-card.jpg', import.meta.url));

async function openIdStep(page: Page) {
  await page.addInitScript((id) => {
    localStorage.setItem('sheout_language', 'en');
    localStorage.setItem('sheout_driver_access_token', 'test-token');
    localStorage.setItem('sheout_driver_account_id', id);
    sessionStorage.setItem('sheout_session_started', '1');
    sessionStorage.setItem('sheout_splash_shown', '1');
  }, ACCOUNT);
  await page.route('**/api/v1/**', (route) => {
    const { pathname } = new URL(route.request().url());
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/driver-verification/me')) {
      return json({ accountId: ACCOUNT, role: 'DRIVER', genderVerificationStatus: 'PENDING', policeVerificationStatus: 'PENDING',
        documentSubmitted: false, rejectionReason: null, updatedAt: now });
    }
    if (pathname.endsWith('/users/driver/me/readiness')) return json({ ready: false, blockers: [], warnings: [], documents: [] });
    if (pathname.endsWith('/driver-verification/consent')) return json({ currentVersion: 'v1', acceptedVersion: 'v1', acceptedAt: now, current: true });
    if (pathname.endsWith('/driver-verification/turnaround')) return json({ typicalMinutes: 30, measured: false, sampleSize: 0, reviewHoursFrom: '09:00', reviewHoursTo: '21:00' });
    if (pathname.endsWith('/users/driver/me')) {
      return json({ accountId: ACCOUNT, name: 'Test Partner', phoneNumber: '+919000000301', vehicleType: 'BIKE', vehicleRegistrationNumber: 'TS09AB1234',
        panNumber: null, onlineStatus: 'OFFLINE', verified: false, profilePhotoUrl: null, hasProfilePhoto: true, dateOfBirth: '1994-03-02',
        email: null, profileComplete: true, updatedAt: now });
    }
    if (pathname.endsWith('/users/me/preferences')) return json({ language: 'en' });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
  await page.goto('/verification', { timeout: 90_000 });
  await expect(page.getByTestId('id-photo')).toBeVisible({ timeout: 60_000 });
}

test('a doubtful ID photo: "Use anyway" carries on to covering the number', async ({ page }) => {
  await openIdStep(page);
  await page.getByTestId('id-photo-input').setInputFiles(DARK);
  const notice = page.getByTestId('id-photo-quality');
  await expect(notice).toBeVisible({ timeout: 20_000 });
  await expect(notice).toContainText('too dark');
  await page.getByTestId('id-photo-quality-use-anyway').click();
  await expect(page.getByTestId('document-masker')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('document-masker')).toBeInViewport();
});

test('the ID is taken with the camera only - there is no file upload for it', async ({ page }) => {
  await openIdStep(page);
  await expect(page.getByTestId('id-photo-camera')).toBeVisible();
  await expect(page.getByTestId('id-photo').getByRole('button', { name: /upload file/i })).toHaveCount(0);
  await expect(page.getByTestId('id-photo-input')).toHaveAttribute('capture', 'environment');
  await expect(page.getByTestId('id-photo-input')).toHaveAttribute('accept', 'image/*');
  await expect(page.locator('body')).toContainText('Photos of a screen, screenshots and photocopies are not accepted');
});

test('a white card is not called glare; a blown-out patch is', async ({ page }) => {
  await openIdStep(page);
  const notice = page.getByTestId('id-photo-quality');
  await page.getByTestId('id-photo-input').setInputFiles(fileURLToPath(new URL('./fixtures/white-card.jpg', import.meta.url)));
  // Wait for the check's answer: the notice (it may say no face was found on this plain card) or, with
  // nothing to say, straight on to covering the number. Either way, no shiny patch.
  await expect(notice.or(page.getByTestId('document-masker'))).toBeVisible({ timeout: 30_000 });
  if (await notice.isVisible()) {
    await expect(notice).not.toContainText('shiny patch');
    await page.getByTestId('id-photo-quality-retake').isVisible();
  } else {
    await page.getByTestId('masker-cancel').click();
  }
  await page.getByTestId('id-photo-input').setInputFiles(fileURLToPath(new URL('./fixtures/glare-card.jpg', import.meta.url)));
  await expect(notice).toContainText('shiny patch', { timeout: 20_000 });
});
