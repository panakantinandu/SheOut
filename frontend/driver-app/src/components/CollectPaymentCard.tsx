import { CheckCircle2, HelpCircle, Hourglass, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { AmountText, Card, IconCircle, paymentMethodLabel } from '@sheout/design-system';
import { paymentsApi } from '../api/client';
import type { PaymentSummary } from '../api/types';
import { useTranslation } from '@sheout/design-system';

const POLL_INTERVAL_MS = 4000;

/**
 * Getting paid for a trip she has just ended.
 * <p>
 * There is nothing for her to press. The rider pays in her own app - from
 * her SheOut wallet or online - and the fare lands in this partner's wallet;
 * this card polls until it does. There used to be a "cash received" button.
 * It was the one way a fare could be marked paid on somebody's word, with no
 * record of the money, and it let a trip be closed whether or not anybody had
 * paid. Taking cash is now against the rules, and the card says so, so she
 * has something to point a rider at.
 */
export function CollectPaymentCard({ bookingId, onPaid }: { bookingId: string; onPaid?: () => void }) {
  const { t } = useTranslation();
  const [payment, setPayment] = useState<PaymentSummary | null>(null);

  const paid = payment?.status === 'CAPTURED' || payment?.status === 'WAIVED';

  useEffect(() => {
    if (paid) onPaid?.();
    // onPaid is a fresh closure on every parent render; firing once when paid is the point.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paid]);

  useEffect(() => {
    if (paid) return;
    let cancelled = false;
    const load = () =>
      paymentsApi
        .getForBooking(bookingId)
        .then((p) => {
          if (!cancelled) setPayment(p);
        })
        // 404 for the moment between the trip ending and its payment row; poll through it.
        .catch(() => undefined);
    load();
    const timer = window.setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [bookingId, paid]);

  if (!payment) {
    return (
      <Card>
        <p className="text-sm text-text-secondary">{t('collect.gettingReady')}</p>
      </Card>
    );
  }

  if (paid) {
    const earned = payment.method !== 'CASH' && payment.status !== 'WAIVED' ? payment.driverPayout : null;
    if (earned != null) {
      // Figures as recorded on the payment when it was captured. The fee is
      // the difference between the two, so the lines always add up to the
      // fare exactly as settled - nothing here is priced again.
      const fare = payment.fareAmount ?? payment.amount;
      const fee = Math.max(0, Math.round((fare - earned) * 100) / 100);
      return (
        <Card tone="success" className="space-y-4" data-testid="collect-payment-paid">
          <div className="flex items-start gap-3">
            <IconCircle size="md" tone="soft" color="green" icon={<CheckCircle2 />} />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-text-secondary">{t('collect.youEarned')}</p>
              <p data-testid="earned-amount">
                <AmountText amount={earned} size="lg" exact animate className="text-4xl leading-tight" />
              </p>
              <p className="text-sm text-text-secondary">{t('collect.inWallet')}</p>
            </div>
          </div>
          <FareBreakdown fare={fare} fee={fee} percent={payment.commissionPercent} />
          <p className="text-xs text-text-secondary">
            {payment.method === 'PROMO_CREDIT'
              ? t('collect.paidByOffer')
              : t('collect.paidBy', { method: paymentMethodLabel(payment.method) })}
          </p>
        </Card>
      );
    }
    return (
      <Card tone="success" className="space-y-2" data-testid="collect-payment-paid">
        <div className="flex items-center gap-3">
          <IconCircle size="md" tone="soft" color="green" icon={<CheckCircle2 />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">{t('collect.paid')}</p>
            <p className="text-sm text-text-secondary">
              {payment.status === 'WAIVED'
                ? t('collect.settledEarlier')
                : payment.method === 'CASH'
                  ? t('collect.cash')
                  : t('collect.paidBy', { method: paymentMethodLabel(payment.method) })}
            </p>
          </div>
          <AmountText amount={payment.amount} size="lg" exact />
        </div>
      </Card>
    );
  }

  return (
    <Card className="space-y-3" data-testid="collect-payment-due">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <IconCircle size="sm" tone="soft" color="orange" icon={<Hourglass />} />
          <p className="font-heading font-semibold text-text-primary">{t('collect.waiting')}</p>
        </div>
        <AmountText amount={payment.amount} size="lg" exact />
      </div>
      <p className="text-sm text-text-secondary">
        {t('collect.howPaid')}
      </p>
      <div className="flex items-start gap-2 rounded-card bg-background p-3 text-sm text-text-secondary">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <p>
          {t('collect.noCash')}
        </p>
      </div>
    </Card>
  );
}

/** Shown open the first time she sees a fee explained, and behind a link after that. */
const FEE_EXPLAINED_KEY = 'sheout_fee_explained_seen';

function readSeen(): boolean {
  try {
    return localStorage.getItem(FEE_EXPLAINED_KEY) === '1';
  } catch {
    return false;
  }
}

/**
 * How the fare was split, as plain facts beside each other: what the rider's
 * trip cost, and SheOut's fee at its rate. Neutral on purpose - no minus
 * signs, no red, no "deducted" - because this is disclosure of how her pay
 * is worked out, not a charge being taken from her.
 * <p>
 * It stays on screen every time, not only on request: gig-worker rules
 * (Telangana's 2026 Platform-Based Gig Workers Act among them) ask that the
 * calculation behind a worker's pay be shown to her.
 */
function FareBreakdown({ fare, fee, percent }: { fare: number; fee: number; percent: number | null }) {
  const { t } = useTranslation();
  const [firstTime] = useState(() => !readSeen());
  const [open, setOpen] = useState(firstTime);

  useEffect(() => {
    if (!firstTime) return;
    try {
      localStorage.setItem(FEE_EXPLAINED_KEY, '1');
    } catch {
      // Private mode: she simply sees the explanation open again next time.
    }
  }, [firstTime]);

  const rate = percent == null ? null : Number(percent).toLocaleString('en-IN', { maximumFractionDigits: 2 });
  return (
    <div className="space-y-2 rounded-card bg-background p-3 text-sm text-text-secondary" data-testid="fare-breakdown">
      <div className="flex justify-between gap-3">
        <span>{t('collect.tripFare')}</span>
        <span className="tabular-nums" data-testid="breakdown-fare">₹{fare.toFixed(2)}</span>
      </div>
      <div className="flex justify-between gap-3">
        <span>{rate == null ? t('collect.platformFee') : t('collect.platformFeeRate', { rate })}</span>
        <span className="tabular-nums" data-testid="breakdown-fee">₹{fee.toFixed(2)}</span>
      </div>
      <button
        type="button"
        className="flex min-h-[44px] items-center gap-1.5 text-left text-xs font-semibold text-primary"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        data-testid="fee-help"
      >
        <HelpCircle className="h-4 w-4 shrink-0" />
        {t('collect.feeHelp')}
      </button>
      {open && (
        <p className="text-xs leading-relaxed" data-testid="fee-explained">
          {t('collect.feeExplained')}
        </p>
      )}
    </div>
  );
}
