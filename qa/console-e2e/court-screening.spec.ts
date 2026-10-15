import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test, type Page } from '@playwright/test';

/**
 * The console's court-record check, end to end in a browser: the real
 * console page (static/admin/index.html), its real script and CSP-safe
 * markup, against a mocked API that walks the screening through its states.
 * The rules themselves (consent first, a second reviewer, no self-approval,
 * hearing her first) are the server's and are tested there
 * (CourtScreeningFlowTest, CourtScreeningConsoleTest); this proves every
 * button calls the right endpoint, shows what comes back, and shows the
 * server's refusal when it says no.
 *
 *   npx playwright test -c qa/console-e2e
 */

const ADMIN = resolve(__dirname, '../../backend/src/main/resources/static/admin');
const PAGE = readFileSync(resolve(ADMIN, 'index.html'), 'utf8');
const TYPES: Record<string, string> = { svg: 'image/svg+xml', js: 'text/javascript', css: 'text/css', png: 'image/png', json: 'application/json' };
const PARTNER = '11111111-2222-4333-8444-555555555555';
/** COURT_SHOTS=<dir> saves a screenshot at each step, for the report. */
const SHOTS = process.env.COURT_SHOTS;
const shot = async (page: Page, name: string) => {
  if (SHOTS) await page.screenshot({ path: resolve(SHOTS, `console-court-${name}.png`) });
};

type Search = { id: string; district: string; nameVariant: string; outcome: string; note: string | null; searchedOn: string; hasScreenshot: boolean };

async function mockConsole(page: Page) {
  const s = {
    exists: false,
    status: 'CONSENT_REQUESTED',
    searches: [] as Search[],
    secondOutcome: null as string | null,
    decision: null as string | null,
    reasonCategory: null as string | null,
    responseDueAt: null as string | null,
    flaggedByMe: false,
  };
  const calls: string[] = [];
  const view = () => ({
    id: 'scr1', accountId: PARTNER, status: s.status, applicantName: 'Lakshmi Devi', dateOfBirth: '1994-03-02',
    consentVersion: s.status === 'CONSENT_REQUESTED' ? null : 'v1', consentAt: s.status === 'CONSENT_REQUESTED' ? null : new Date().toISOString(),
    nameVariants: ['Lakshmi Devi', 'Laxmi Devi'], relativeName: null, districts: ['Hyderabad', 'Rangareddy', 'Medchal-Malkajgiri'],
    secondOutcome: s.secondOutcome, secondNote: s.secondOutcome ? 'checked' : null, partnerResponse: null,
    responseDueAt: s.responseDueAt, decision: s.decision, reasonCategory: s.reasonCategory, decisionNote: null,
    searches: s.searches, eCourtsSearchUrl: 'https://services.ecourts.gov.in/ecourtindia_v6/?p=casestatus/index',
    disclaimer: 'A screening aid, not police verification. Never tell anyone a partner is police or background verified because of it.',
  });
  await page.route('**/*', async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    if (!url.pathname.startsWith('/api/')) {
      if (url.pathname === '/admin/' || url.pathname === '/admin') return route.fulfill({ status: 200, contentType: 'text/html', body: PAGE });
      const file = resolve(ADMIN, url.pathname.replace(/^\/admin\//, ''));
      if (url.pathname.startsWith('/admin/') && file.startsWith(ADMIN) && existsSync(file)) {
        return route.fulfill({ status: 200, contentType: TYPES[file.split('.').pop() ?? ''] ?? 'application/octet-stream', body: readFileSync(file) });
      }
      return route.fulfill({ status: 404, body: '' });
    }
    const path = url.pathname.replace('/api/v1', '');
    const method = req.method();
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    const refuse = (error: string, message: string) => json({ error, message }, 409);
    if (path === '/admin/auth/me') {
      return json({ staffId: 's1', accountId: 'a1', role: 'VERIFICATION_AGENT', roleLabel: 'Verification', displayName: 'Reviewer',
        email: 'reviewer@example.com', permissions: ['verification.review'], legacy: false, csrfToken: 't',
        passkey: { required: false, count: 1 }, idleTimeoutSeconds: 1800, absoluteExpiresAt: new Date(Date.now() + 3600e3).toISOString() });
    }
    const base = `/admin/court-screenings/${PARTNER}`;
    if (path.startsWith('/admin/court-screenings')) calls.push(`${method} ${path.replace(PARTNER, 'P')}`);
    if (path === '/admin/court-screenings' && method === 'GET') return json(s.exists ? [{ accountId: PARTNER, status: s.status, updatedAt: new Date().toISOString(), responseDueAt: s.responseDueAt }] : []);
    if (path === '/admin/court-screenings/report') return json({ total: s.exists ? 1 : 0, byStatus: {}, flagged: 0, awaitingSecondSignOff: 0, responseOverdue: 0, averageHoursConsentToDecision: null });
    if (path === base && method === 'POST') { s.exists = true; return json(view()); }
    if (path === base && method === 'GET') return s.exists ? json(view()) : json({ error: 'Not Found' }, 404);
    if (path === `${base}/searches` && method === 'POST') {
      if (s.status === 'CONSENT_REQUESTED') return refuse('CONSENT_REQUIRED', 'She has not agreed to this search yet. Nothing may be searched before she does.');
      const body = req.postDataJSON();
      if (body.outcome !== 'NO_MATCH' && !body.note) return json({ error: 'NOTE_REQUIRED', message: 'Write a note.' }, 400);
      s.searches.push({ id: `x${s.searches.length}`, district: body.district, nameVariant: body.nameVariant, outcome: body.outcome,
        note: body.note, searchedOn: '2026-10-14', hasScreenshot: false });
      if (body.outcome === 'POSSIBLE_MATCH' || body.outcome === 'CONFIRMED_MATCH') { s.status = 'MATCH_TO_CONFIRM'; s.flaggedByMe = true; }
      return json(s.searches[s.searches.length - 1]);
    }
    if (path === `${base}/second-signoff`) {
      if (s.flaggedByMe) return refuse('SAME_REVIEWER', 'A second reviewer must sign this off: someone other than the reviewer who flagged it.');
      s.secondOutcome = 'AGREE'; s.status = 'MATCH_CONFIRMED'; return json(view());
    }
    if (path === `${base}/notify`) { s.status = 'AWAITING_RESPONSE'; s.responseDueAt = new Date(Date.now() + 7 * 864e5).toISOString(); return json(view()); }
    if (path === `${base}/decision`) {
      const body = req.postDataJSON();
      if (body.decision === 'REJECT' && !body.reasonCategory) return json({ error: 'REASON_REQUIRED', message: 'Choose a reason category.' }, 400);
      s.decision = body.decision; s.reasonCategory = body.reasonCategory; s.status = body.decision === 'REJECT' ? 'REJECTED' : 'APPROVED';
      return json(view());
    }
    if (method === 'GET') return json([]);
    return json({});
  });
  return {
    state: s,
    calls,
    /** The partner agrees in her app. */
    consent: () => { s.status = 'SEARCHING'; },
    /** Another reviewer is signed in now. */
    otherReviewer: () => { s.flaggedByMe = false; },
  };
}

test('a reviewer starts a check, logs searches, gets a second sign-off and decides', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const mock = await mockConsole(page);
  await page.goto('http://console.test/admin/');
  await page.locator('[data-tab="courtchecks"]').click();
  await expect(page.getByText('Start a court-record check')).toBeVisible();
  await shot(page, 'tab');

  await page.fill('#courtAccount', PARTNER);
  await page.fill('#courtHome', 'Warangal');
  await page.click('#courtStart');
  const dialog = page.getByRole('dialog', { name: 'Court-record check' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Waiting for her consent')).toBeVisible();
  await expect(dialog.getByText('not police verification')).toBeVisible();
  await shot(page, 'consent');
  // No search form before she agrees.
  await expect(dialog.locator('#csOutcome')).toHaveCount(0);
  await dialog.getByRole('button', { name: 'Close' }).click();

  mock.consent();
  await page.getByRole('button', { name: 'Open' }).click();
  await expect(dialog.getByText('Searching', { exact: true })).toBeVisible();
  await expect(dialog.getByRole('link', { name: 'Open eCourts party-name search' })).toHaveAttribute('target', '_blank');

  // "Possible match" without a note: the server's refusal is shown.
  await dialog.locator('#csOutcome').selectOption('POSSIBLE_MATCH');
  await dialog.getByRole('button', { name: 'Save this search' }).click();
  await expect(dialog.getByRole('alert')).toHaveText('Write a note.');

  await dialog.locator('#csNote').fill('Same name and father\'s name, age close');
  await dialog.getByRole('button', { name: 'Save this search' }).click();
  await expect(dialog.getByText('Match - needs a second reviewer')).toBeVisible();
  await shot(page, 'match');

  // The flagging reviewer cannot sign it off.
  await dialog.locator('#cvSecondNote').fill('looks right');
  await dialog.getByRole('button', { name: 'It may be her' }).click();
  await expect(dialog.getByRole('alert')).toContainText('someone other than the reviewer who flagged it');
  await shot(page, 'same-reviewer');

  mock.otherReviewer();
  await dialog.getByRole('button', { name: 'It may be her' }).click();
  await expect(dialog.getByText('Match confirmed - tell her')).toBeVisible();
  await dialog.getByRole('button', { name: 'Tell her and start her response window' }).click();
  await expect(dialog.getByText('Waiting for her answer')).toBeVisible();
  await shot(page, 'awaiting-answer');

  await dialog.locator('#cdDecision').selectOption('REJECT');
  await dialog.getByRole('button', { name: 'Record the decision' }).click();
  await expect(dialog.getByRole('alert')).toHaveText('Choose a reason category.');
  await dialog.locator('#cdReason').selectOption('RECORD_CONFIRMED');
  await dialog.getByRole('button', { name: 'Record the decision' }).click();
  await expect(dialog.locator('.badge', { hasText: 'Not cleared' })).toBeVisible();
  await shot(page, 'decided');

  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
  expect(mock.calls).toEqual(expect.arrayContaining([
    'POST /admin/court-screenings/P', 'POST /admin/court-screenings/P/searches', 'POST /admin/court-screenings/P/second-signoff',
    'POST /admin/court-screenings/P/notify', 'POST /admin/court-screenings/P/decision',
  ]));
  expect(errors).toEqual([]);
});
