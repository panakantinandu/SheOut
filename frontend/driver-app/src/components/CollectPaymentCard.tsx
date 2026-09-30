import { CheckCircle2, HelpCircle, Hourglass, QrCode, RefreshCw, ShieldCheck } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { AmountText, Button, Card, IconCircle, paymentMethodLabel } from '@sheout/design-system';
import { paymentsApi, type UpiQr } from '../api/client';
import { apiErrorText } from '../lib/apiErrors';
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
export function CollectPaymentCard({ bookingId, onPaid }: { bookingId: string; onPaid?: (payment: PaymentSummary, justNow: boolean) => void }) {
  const { t } = useTranslation();
  const [payment, setPayment] = useState<PaymentSummary | null>(null);
  /** The UPI QR she is showing, if she opened it. While it is up, each poll asks Razorpay too. */
  const [qr, setQr] = useState<UpiQr | null>(null);
  const [qrBusy, setQrBusy] = useState(false);
  const [qrError, setQrError] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const qrExpired = qr ? new Date(qr.expiresAt).getTime() <= now : false;

  const paid = payment?.status === 'CAPTURED' || payment?.status === 'WAIVED';
  /**
   * This screen saw the fare still owed before it saw it paid - so the capture
   * happened while she was watching, and is worth marking. Opened from
   * history, a paid trip is just a record.
   */
  const sawUnpaid = useRef(false);
  if (payment && !paid) sawUnpaid.current = true;

  useEffect(() => {
    if (paid && payment) onPaid?.(payment, sawUnpaid.current);
    // onPaid is a fresh closure on every parent render; firing once when paid is the point.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paid]);

  useEffect(() => {
    if (paid) return;
    let cancelled = false;
    const load = () =>
      (qr && !qrExpired ? paymentsApi.checkUpiQr(bookingId) : paymentsApi.getForBooking(bookingId))
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
  }, [bookingId, paid, qr, qrExpired]);

  // The clock the QR's expiry is read against.
  useEffect(() => {
    if (!qr || paid) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [qr, paid]);

  async function showQr() {
    setQrBusy(true);
    setQrError(null);
    try {
      setQr(await paymentsApi.upiQr(bookingId));
      setNow(Date.now());
    } catch (err) {
      setQrError(apiErrorText(err, 'collect.qrError'));
    } finally {
      setQrBusy(false);
    }
  }

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
            <p className="font-heading text-card-title text-text-primary">{t('collect.paid')}</p>
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
          <p className="font-heading text-card-title text-text-primary">{t('collect.waiting')}</p>
        </div>
        <AmountText amount={payment.amount} size="lg" exact />
      </div>
      <p className="text-sm text-text-secondary">
        {t('collect.howPaid')}
      </p>
      {qr && !qrExpired ? (
        <div className="flex flex-col items-center gap-2 rounded-card border border-border bg-white p-4 text-center" data-testid="upi-qr">
          <img src={qr.imageUrl} alt={t('collect.qrAlt')} className="w-full max-w-[18rem]" />
          <p className="text-sm font-semibold text-[#1a1a1a]">{t('collect.qrScan')}</p>
          <p className="text-xs text-[#555]">
            {t('collect.qrValidTill', { time: new Date(qr.expiresAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) })}
          </p>
        </div>
      ) : (
        <Button
          fullWidth
          variant={qr ? 'secondary' : 'primary'}
          icon={qr ? <RefreshCw className="h-4 w-4" /> : <QrCode className="h-4 w-4" />}
          onClick={showQr}
          disabled={qrBusy}
          data-testid="show-upi-qr"
        >
          {qrBusy ? t('collect.qrLoading') : qr ? t('collect.qrNew') : t('collect.qrShow')}
        </Button>
      )}
      {qrError && <p className="text-sm text-danger" role="alert">{qrError}</p>}
      <div className="flex items-start gap-2 rounded-card bg-background p-3 text-sm text-text-secondary">
        <ShieldCheck className="mt-1 h-4 w-4 shrink-0 text-primary" />
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
        className="flex min-h-[44px] items-center gap-2 text-left text-xs font-semibold text-primary"
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
