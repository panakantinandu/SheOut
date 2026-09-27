import { Copy, Gift, MessageCircle, Share2, UserPlus, Wallet } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';
import { Card } from './Card';
import { IconCircle } from './IconCircle';
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
