import { Gift, Share2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, TopHeader, brandIllustration, showToast, useTranslation } from '@sheout/design-system';

const APP_URL = 'https://app.sheoutride.com';

/**
 * Refer a Friend - a placeholder, because there is no referral programme.
 * <p>
 * FLAGGED: no invite codes, rewards or tracking exist, and none were built
 * as a side effect of adding this menu entry. The one working action shares
 * a plain link to the app through the phone's share sheet; it carries no
 * code and records nothing.
 */
export function Refer() {
  const { t } = useTranslation();
  const navigate = useNavigate();

  async function share() {
    const text = t('refer.shareText');
    try {
      if (typeof navigator.share === 'function') {
        await navigator.share({ title: 'SheOut', text, url: APP_URL });
        return;
      }
      await navigator.clipboard?.writeText(`${text} ${APP_URL}`);
      showToast(t('refer.copied'));
    } catch {
      // She closed the share sheet - nothing to do.
    }
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('drawer.refer')} onBack={() => navigate(-1)} />
      <Card variant="primary" className="relative overflow-hidden">
        <span className="inline-block rounded-full bg-accent-orange px-3 py-1 text-xs font-bold uppercase tracking-widest text-text-inverse">
          {t('drawer.comingSoon')}
        </span>
        <div className="relative z-10 mt-3 max-w-[62%]">
          <p className="font-heading text-xl font-bold">{t('refer.headline')}</p>
          <p className="mt-1 text-sm opacity-90">{t('refer.subhead')}</p>
        </div>
        <img
          src={brandIllustration}
          alt=""
          aria-hidden="true"
          className="pointer-events-none absolute -bottom-3 -right-3 h-28 w-28 object-contain opacity-95"
        />
      </Card>
      <Card className="flex items-start gap-3">
        <IconCircle tone="soft" color="orange" icon={<Gift />} />
        <p className="flex-1 text-sm text-text-secondary">{t('refer.notYet')}</p>
      </Card>
      <Button fullWidth variant="secondary" icon={<Share2 className="h-4 w-4" />} onClick={share}>
        {t('refer.share')}
      </Button>
    </div>
  );
}
