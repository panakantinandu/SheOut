import { Copy, Gift, MessageCircle, PartyPopper, Share2, UserPlus, Wallet } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';
import { Card } from './Card';
import { IconCircle } from './IconCircle';
import { Overlay } from './Overlay';
import brandIllustration from '../assets/sheout-illustration.webp';
import { showToast } from '../lib/toast';
import type { ReferralSummary } from '../lib/referral';

const rupees = (n: number) => `₹${Number.isInteger(n) ? n : n.toFixed(2)}`;

/**
 * The body of Refer a Friend, the same in both apps: her code and link, a way
 * to share them, how many friends have come and what she has earned, and
 * what the programme gives each side today.
 * <p>
 * The amounts are the running campaigns' own figures from the server. When a
 * side is paused or out of budget the screen says so rather than promising a
 * reward that would not be paid.
 */
export function ReferAFriend({ summary }: { summary: ReferralSummary }) {
  const { t } = useTranslation('ds');
  const who = summary.cashReward ? 'partner' : 'rider';
  const running = summary.referrerAmount != null && summary.refereeAmount != null;
  const message = t(`refer.${who}.message`, { code: summary.code, amount: rupees(summary.refereeAmount ?? 0) });
  const fullText = `${message} ${summary.shareUrl}`;
  const atLimit = summary.rewarded >= summary.maxRewarded;

  async function share() {
    try {
      if (typeof navigator.share === 'function') {
        await navigator.share({ title: 'SheOut', text: message, url: summary.shareUrl });
        return;
      }
      await navigator.clipboard?.writeText(fullText);
      showToast(t('refer.copiedLink'));
    } catch {
      // She closed the share sheet.
    }
  }

  async function copyCode() {
    try {
      await navigator.clipboard?.writeText(summary.code);
      showToast(t('refer.copiedCode'));
    } catch {
      // Clipboard blocked; the code is on screen to read out.
    }
  }

  return (
    <div className="space-y-4" data-testid="refer-a-friend">
      <Card variant="primary" className="relative overflow-hidden">
        <div className="relative z-10 max-w-[64%]">
          <p className="font-heading text-title">
            {running
              ? t(`refer.${who}.headline`, { mine: rupees(summary.referrerAmount!), theirs: rupees(summary.refereeAmount!) })
              : t('refer.pausedHeadline')}
          </p>
          <p className="mt-1 text-sm opacity-90">{running ? t(`refer.${who}.subhead`, { mine: rupees(summary.referrerAmount!), theirs: rupees(summary.refereeAmount!) }) : t('refer.pausedBody')}</p>
        </div>
        <img src={brandIllustration} alt="" aria-hidden="true"
          className="pointer-events-none absolute -bottom-3 -right-3 h-28 w-28 object-contain opacity-95" />
      </Card>

      <Card className="space-y-3">
        <p className="text-caption uppercase tracking-wide text-text-secondary">{t('refer.yourCode')}</p>
        <div className="flex items-center justify-between gap-3 rounded-input border border-dashed border-primary bg-primary-light px-4 py-3">
          <span className="font-heading text-title tracking-[0.2em] text-primary" data-testid="referral-code">{summary.code}</span>
          <button type="button" onClick={copyCode} aria-label={t('refer.copyCode')}
            className="flex h-10 w-10 items-center justify-center rounded-full text-primary hover:bg-surface">
            <Copy className="h-5 w-5" />
          </button>
        </div>
        <p className="break-all text-xs text-text-secondary" data-testid="referral-link">{summary.shareUrl}</p>
        <Button fullWidth icon={<Share2 className="h-4 w-4" />} onClick={share} data-testid="referral-share">
          {t('refer.share')}
        </Button>
        <a
          className="flex w-full items-center justify-center gap-2 rounded-full border border-border py-3 text-sm font-semibold text-text-primary"
          href={`https://wa.me/?text=${encodeURIComponent(fullText)}`}
          target="_blank"
          rel="noopener noreferrer"
          data-testid="referral-whatsapp"
        >
          <MessageCircle className="h-4 w-4" /> {t('refer.whatsapp')}
        </a>
      </Card>

      <div className="grid grid-cols-2 gap-3">
        <Card className="space-y-1" data-testid="referral-successful">
          <IconCircle tone="soft" size="sm" icon={<UserPlus />} />
          <p className="font-heading text-title text-text-primary">{summary.successful}</p>
          <p className="text-caption text-text-secondary">{t(`refer.${who}.successfulLabel`)}</p>
        </Card>
        <Card className="space-y-1" data-testid="referral-earned">
          <IconCircle tone="soft" size="sm" color="green" icon={summary.cashReward ? <Wallet /> : <Gift />} />
          <p className="font-heading text-title text-text-primary">{rupees(summary.totalEarned)}</p>
          <p className="text-caption text-text-secondary">{t(`refer.${who}.earnedLabel`)}</p>
        </Card>
      </div>
      {(summary.pending > 0 || atLimit) && (
        <p className="text-sm text-text-secondary">
          {summary.pending > 0 && t(`refer.${who}.pending`, { count: summary.pending })}
          {summary.pending > 0 && atLimit && ' '}
          {atLimit && t('refer.atLimit', { max: summary.maxRewarded })}
        </p>
      )}

      {summary.joinedWith && summary.joinedWith.status !== 'REJECTED' && (
        <Card tone={summary.joinedWith.status === 'COMPLETED' ? 'success' : 'brand'} className="text-sm" data-testid="referral-joined">
          {summary.joinedWith.status === 'COMPLETED'
            ? t(`refer.${who}.joinedDone`, { amount: rupees(summary.joinedWith.reward ?? 0) })
            : t(`refer.${who}.joinedPending`, { amount: rupees(summary.refereeAmount ?? 0) })}
        </Card>
      )}

      <Card className="space-y-2">
        <p className="font-heading text-card-title text-text-primary">{t('refer.howTitle')}</p>
        <ol className="list-decimal space-y-1 pl-5 text-sm text-text-secondary">
          <li>{t(`refer.${who}.step1`)}</li>
          <li>{t(`refer.${who}.step2`)}</li>
          <li>{t(`refer.${who}.step3`, { max: summary.maxRewarded })}</li>
        </ol>
      </Card>
    </div>
  );
}

/**
 * "Have a referral code?" on the signup profile screen - optional, and shown
 * only while the server says this account may still use one.
 */
export function ReferralCodeField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { t } = useTranslation('ds');
  return (
    <div>
      <label htmlFor="referral-code" className="mb-1 block text-sm font-medium text-text-primary">{t('refer.fieldLabel')}</label>
      <input
        id="referral-code"
        value={value}
        onChange={(e) => onChange(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
        maxLength={12}
        autoCapitalize="characters"
        autoComplete="off"
        placeholder={t('refer.fieldPlaceholder')}
        className="w-full rounded-input border border-border bg-surface px-4 py-3 font-heading tracking-[0.15em] text-text-primary placeholder:font-sans placeholder:tracking-normal placeholder:text-text-secondary"
        data-testid="referral-code-input"
      />
      <p className="mt-1 text-xs text-text-secondary">{t('refer.fieldHelp')}</p>
    </div>
  );
}

/** What the server says once her friend's code is accepted - see referralsApi.apply. */
export interface ReferralWelcomeDetails {
  /** The friend who invited her, first name only; null when that friend has not given one. */
  referrerFirstName: string | null;
  /** What her first paid trip brings; null while the welcome reward is paused, so nothing is promised. */
  reward: number | null;
  /** Partners are paid into their wallet; riders get ride credit. */
  cashReward: boolean;
}

// Where each piece of confetti starts, how far it drifts, and when it falls.
// Fixed rather than random, so the burst looks the same every time and in
// every screenshot.
const CONFETTI = Array.from({ length: 22 }, (_, i) => ({
  left: `${(i * 37 + 7) % 100}%`,
  drift: `${((i * 53) % 90) - 45}px`,
  delay: `${(i % 7) * 90}ms`,
  colour: ['bg-primary', 'bg-accent-orange', 'bg-accent-green', 'bg-accent-blue', 'bg-primary-mid'][i % 5],
  shape: i % 3 === 0 ? 'h-2 w-2 rounded-full' : 'h-3 w-1.5 rounded-sm',
}));

/**
 * The congratulations a new rider or partner sees the moment she has signed
 * up with a friend's code.
 * <p>
 * The one place in the app that celebrates on purpose. SuccessCheck explains
 * why the end of a trip does not; this is different - it only ever follows
 * something good, she chose to act on a friend's invite, and it says plainly
 * what that has earned and when. The reward comes with her first paid trip,
 * not now, and the copy says so rather than letting her think it is already
 * in her balance.
 */
export function ReferralWelcome({ details, onContinue }: { details: ReferralWelcomeDetails | null; onContinue: () => void }) {
  const { t } = useTranslation('ds');
  const who = details?.cashReward ? 'partner' : 'rider';
  const name = details?.referrerFirstName ?? null;
  return (
    <Overlay open={details != null} label={t('refer.welcome.title')} onDismiss={onContinue} className="px-4">
      <div
        className="relative w-full overflow-hidden rounded-card bg-surface px-6 pb-6 pt-8 text-center shadow-xl motion-safe:animate-pop-in"
        data-testid="referral-welcome"
      >
        <div className="pointer-events-none absolute inset-x-0 top-0 h-full motion-reduce:hidden" aria-hidden="true">
          {CONFETTI.map((c, i) => (
            <span
              key={i}
              className={`absolute top-0 ${c.shape} ${c.colour} animate-confetti-fall`}
              style={{ left: c.left, animationDelay: c.delay, ['--confetti-drift' as string]: c.drift }}
            />
          ))}
        </div>
        <div className="relative">
          <span className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-primary-light text-primary motion-safe:animate-nav-pop">
            <PartyPopper className="h-10 w-10" aria-hidden="true" />
          </span>
          <p className="mt-5 text-sm font-semibold uppercase tracking-[0.18em] text-primary">{t('refer.welcome.kicker')}</p>
          <h2 className="mt-1 font-heading text-title text-text-primary">{t('refer.welcome.title')}</h2>
          <p className="mt-3 text-text-secondary">
            {name ? t('refer.welcome.invitedBy', { name }) : t('refer.welcome.invitedByFriend')}
          </p>
          {details?.reward != null && (
            <div className="mt-5 rounded-input bg-primary-light px-4 py-4" data-testid="referral-welcome-reward">
              <p className="font-heading text-3xl text-primary">{rupees(details.reward)}</p>
              <p className="mt-1 text-sm font-medium text-text-primary">{t(`refer.welcome.${who}Reward`)}</p>
            </div>
          )}
          <Button className="mt-6 w-full" size="lg" onClick={onContinue} data-testid="referral-welcome-continue">
            {t('refer.welcome.continue')}
          </Button>
        </div>
      </div>
    </Overlay>
  );
}
