import { Gift } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Button, Card, IconCircle, TextField } from '@sheout/design-system';
import { promotionsApi } from '../api/client';
import type { HeldPromotion } from '../api/types';
import { apiErrorText } from '../lib/apiErrors';
import { useTranslation } from '@sheout/design-system';

/**
 * Offers she holds - the signup credit, codes she has entered - and where to
 * enter a new code. Kept apart from the wallet balance above it: credit here
 * is SheOut's money, spent only as a discount on her own fares, never
 * withdrawn or topped up.
 */
export function PromotionsCard() {
  const { t, i18n } = useTranslation();
  const [held, setHeld] = useState<HeldPromotion[] | null>(null);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: 'danger' | 'success'; text: string } | null>(null);

  const load = useCallback(() => {
    promotionsApi.mine().then(setHeld).catch(() => setHeld([]));
  }, []);

  useEffect(load, [load]);

  const redeem = async () => {
    if (!code.trim()) return;
    setBusy(true);
    setMessage(null);
    try {
      await promotionsApi.redeem(code.trim());
      setCode('');
      setMessage({ tone: 'success', text: t('promo.codeAdded') });
      load();
    } catch (err) {
      setMessage({ tone: 'danger', text: apiErrorText(err, 'promo.codeError') });
    } finally {
      setBusy(false);
    }
  };

  const date = (iso: string) => new Date(iso).toLocaleDateString(i18n.language, { day: 'numeric', month: 'short' });

  return (
    <Card className="space-y-3" data-testid="promotions-card">
      <div className="flex items-center gap-3">
        <IconCircle tone="soft" size="sm" icon={<Gift />} />
        <p className="font-heading font-semibold text-text-primary">{t('promo.title')}</p>
      </div>

      {held && held.length === 0 && <p className="text-sm text-text-secondary">{t('promo.none')}</p>}
      {held?.map((p, i) => (
        <div key={i} className="rounded-xl bg-primary-light px-3 py-2 text-sm" data-testid="held-promotion">
          <p className="font-semibold text-text-primary">
            {p.type === 'SIGNUP_CREDIT' && p.creditLeft !== null
              ? t('promo.creditLeft', { name: p.name, amount: p.creditLeft.toFixed(0) })
              : p.name}
          </p>
          <p className="text-xs text-text-secondary">
            {p.type === 'SIGNUP_CREDIT' ? t('promo.creditHow') : t('promo.discountHow')}
            {p.expiresAt && ` · ${t('promo.until', { date: date(p.expiresAt) })}`}
          </p>
        </div>
      ))}

      <div className="flex items-end gap-2">
        <div className="flex-1">
          <TextField
            label={t('promo.codeLabel')}
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            maxLength={20}
            autoCapitalize="characters"
            data-testid="promo-code-input"
          />
        </div>
        <Button size="md" variant="secondary" disabled={busy || !code.trim()} onClick={redeem} data-testid="promo-code-apply">
          {t('promo.apply')}
        </Button>
      </div>
      {message && <p className={`text-sm ${message.tone === 'danger' ? 'text-danger' : 'text-success'}`}>{message.text}</p>}
    </Card>
  );
}
