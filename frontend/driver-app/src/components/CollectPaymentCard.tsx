import { Banknote, CheckCircle2 } from 'lucide-react';
import { useEffect, useState } from 'react';
import { AmountText, Button, Card, ConfirmDialog, IconCircle, paymentMethodLabel } from '@sheout/design-system';
import { ApiError, paymentsApi } from '../api/client';
import type { PaymentSummary } from '../api/types';

const POLL_INTERVAL_MS = 4000;

/**
 * Getting paid for a trip she has just finished.
 * <p>
 * The rider either pays online in her own app, which this picks up by
 * polling, or hands over cash, which only the partner can confirm - she is the
 * one holding it. Confirming cash is behind a dialog because it cannot be
 * undone from the app, and a stray tap would mark an unpaid fare paid.
 */
export function CollectPaymentCard({ bookingId }: { bookingId: string }) {
  const [payment, setPayment] = useState<PaymentSummary | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const captured = payment?.status === 'CAPTURED';

  useEffect(() => {
    if (captured) return;
    let cancelled = false;
    const load = () =>
      paymentsApi
        .getForBooking(bookingId)
        .then((p) => {
          if (!cancelled) setPayment(p);
        })
        // 404 until the payment row is written just after completion; poll through it.
        .catch(() => undefined);
    load();
    const timer = window.setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [bookingId, captured]);

  const handleConfirmCash = async () => {
    setBusy(true);
    setError(null);
    try {
      setPayment(await paymentsApi.confirmCash(bookingId));
      setConfirming(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // Paid already - the rider paid online while the dialog was open.
        setPayment(await paymentsApi.getForBooking(bookingId).catch(() => payment));
        setConfirming(false);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not confirm the cash payment');
        setConfirming(false);
      }
    } finally {
      setBusy(false);
    }
  };

  if (!payment) {
    return (
      <Card>
        <p className="text-sm text-text-secondary">Getting the fare ready...</p>
      </Card>
    );
  }

  if (captured) {
    return (
      <Card tone="success" className="space-y-2" data-testid="collect-payment-paid">
        <div className="flex items-center gap-3">
          <IconCircle size="md" tone="soft" color="green" icon={<CheckCircle2 />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">Fare paid</p>
            <p className="text-sm text-text-secondary">
              {payment.method === 'CASH' ? 'Cash, collected by you' : `Online · ${paymentMethodLabel(payment.method)}`}
            </p>
          </div>
          <AmountText amount={payment.amount} size="lg" exact />
        </div>
        {payment.driverPayout != null && (
          <p className="text-sm text-text-secondary">
            Your share is ₹{payment.driverPayout.toFixed(2)}.
            {payment.method === 'CASH'
              ? ' You already hold the full fare, so SheOut\'s commission comes off your wallet balance.'
              : ' It has been added to your wallet.'}
          </p>
        )}
      </Card>
    );
  }

  return (
    <Card className="space-y-3" data-testid="collect-payment-due">
      <div className="flex items-center justify-between">
        <p className="font-heading font-semibold text-text-primary">Collect payment</p>
        <AmountText amount={payment.amount} size="lg" exact />
      </div>
      <p className="text-sm text-text-secondary">
        Your rider can pay online from her app - this updates on its own. If she pays you in cash, confirm it here once
        it is in your hand.
      </p>
      <Button fullWidth size="md" variant="success" icon={<Banknote className="h-4 w-4" />} disabled={busy}
        onClick={() => setConfirming(true)}>
        Cash received
      </Button>
      {error && <p className="text-sm text-danger">{error}</p>}
      <ConfirmDialog
        open={confirming}
        title={`Received ₹${payment.amount.toFixed(2)} in cash?`}
        message="Only confirm once the full fare is in your hand. This marks the trip paid and cannot be undone from the app."
        confirmLabel={busy ? 'Confirming...' : 'Yes, cash received'}
        onConfirm={handleConfirmCash}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}
