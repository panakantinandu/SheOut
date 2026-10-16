import { expect, test, type Page } from '@playwright/test';

/**
 * The Verification screen counts in the four stages its tracker shows - not a
 * separate "1 of 8" - and the insurance step asks about commercial use only
 * when her vehicle needs a commercial policy (an auto or a cab), never for a
 * bike taxi on her own bike.
 */

test.describe.configure({ timeout: 120_000 });

const ACCOUNT = '00000000-0000-4000-8000-0000000000bb';
const now = new Date().toISOString();
const doc = (type: string, commercialUseRequired = false) => ({
  type, required: true, status: null, documentNumber: null, issuedOn: null, validUntil: null, rejectionReason: null,
  insuranceUseType: null, submittedAt: null, renewalUnderReview: false, rejectionCode: null, commercialUseRequired,
});

async function open(page: Page, vehicleType: 'BIKE' | 'AUTO') {
  await page.addInitScript((id) => {
    localStorage.setItem('sheout_language', 'en');
    localStorage.setItem('sheout_driver_access_token', 'test-token');
    localStorage.setItem('sheout_driver_account_id', id);
    sessionStorage.setItem('sheout_session_started', '1');
    sessionStorage.setItem('sheout_splash_shown', '1');
  }, ACCOUNT);
  const commercial = vehicleType === 'AUTO';
  const docs = ['DRIVING_LICENCE', 'VEHICLE_RC', 'VEHICLE_INSURANCE', 'PUC'].map((t) => doc(t, t === 'VEHICLE_INSURANCE' && commercial));
  await page.route('**/api/v1/**', (route) => {
    const { pathname } = new URL(route.request().url());
    const json = (body: unknown) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    if (pathname.endsWith('/driver-verification/me')) {
      return json({ accountId: ACCOUNT, role: 'DRIVER', genderVerificationStatus: 'VERIFIED', policeVerificationStatus: 'PENDING',
        documentSubmitted: true, rejectionReason: null, updatedAt: now });
    }
    if (pathname.endsWith('/users/driver/me/readiness')) return json({ ready: false, blockers: [], warnings: [], documents: [...docs, doc('POLICE_CERTIFICATE')].map((d) => d.type === 'POLICE_CERTIFICATE' ? { ...d, required: false } : d) });
    if (pathname.endsWith('/driver-verification/consent')) return json({ currentVersion: 'v1', acceptedVersion: 'v1', acceptedAt: now, current: true });
    if (pathname.endsWith('/driver-verification/turnaround')) return json({ typicalMinutes: 30, measured: false, sampleSize: 0, reviewHoursFrom: '09:00', reviewHoursTo: '21:00' });
    if (pathname.endsWith('/users/driver/me')) {
      return json({ accountId: ACCOUNT, name: 'Test Partner', phoneNumber: '+919000000301', vehicleType, vehicleRegistrationNumber: 'TS09AB1234',
        panNumber: null, onlineStatus: 'OFFLINE', verified: false, profilePhotoUrl: null, hasProfilePhoto: true, dateOfBirth: '1994-03-02',
        email: null, profileComplete: true, updatedAt: now });
    }
    if (pathname.endsWith('/users/me/preferences')) return json({ language: 'en' });
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{"error":"NOT_FOUND"}' });
  });
  await page.goto('/verification', { timeout: 90_000 });
  await expect(page.getByTestId('verify-progress')).toBeVisible({ timeout: 60_000 });
}

test('progress is counted in the four stages, and the next step says where it sits in its stage', async ({ page }) => {
  await open(page, 'BIKE');
  // Identity done; licence and vehicle not started; safety check and ready not done.
  await expect(page.getByTestId('verify-progress')).toHaveText('1 of 4 stages done');
  await expect(page.getByTestId('verify-next')).toBeVisible();
  await expect(page.getByTestId('verify-step-in-stage')).toHaveText(/Licence and vehicle · step 1 of 4/);
  await expect(page.locator('body')).not.toContainText(/of 8 done|\b13%/);
});

for (const vehicle of ['BIKE', 'AUTO'] as const) {
  test(`insurance step on a ${vehicle.toLowerCase()} ${vehicle === 'BIKE' ? 'does not ask' : 'asks'} about commercial use`, async ({ page }) => {
    await open(page, vehicle);
    await page.getByTestId('step-VEHICLE_INSURANCE').getByRole('button').first().click();
    await expect(page.getByTestId('doc-send-VEHICLE_INSURANCE')).toBeVisible({ timeout: 20_000 });
    const asked = page.getByText('Is this a commercial policy (for carrying customers or goods for money)?');
    if (vehicle === 'BIKE') await expect(asked).toHaveCount(0);
    else await expect(asked).toBeVisible();
  });
}
