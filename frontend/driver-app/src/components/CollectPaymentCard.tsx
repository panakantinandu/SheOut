import { CheckCircle2, Hourglass, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { AmountText, Card, IconCircle, paymentMethodLabel } from '@sheout/design-system';
import { paymentsApi } from '../api/client';
import type { PaymentSummary } from '../api/types';

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
        <p className="text-sm text-text-secondary">Getting the fare ready...</p>
      </Card>
    );
  }

  if (paid) {
    return (
      <Card tone="success" className="space-y-2" data-testid="collect-payment-paid">
        <div className="flex items-center gap-3">
          <IconCircle size="md" tone="soft" color="green" icon={<CheckCircle2 />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">Fare paid</p>
            <p className="text-sm text-text-secondary">
              {payment.status === 'WAIVED'
                ? 'Settled before in-app payment was required'
                : payment.method === 'CASH'
                  ? 'Cash, collected by you'
                  : `Paid by your rider · ${paymentMethodLabel(payment.method)}`}
            </p>
          </div>
          <AmountText amount={payment.amount} size="lg" exact />
        </div>
        {payment.driverPayout != null && payment.method !== 'CASH' && (
          <p className="text-sm text-text-secondary">
            Your share, ₹{payment.driverPayout.toFixed(2)}, is in your wallet.
          </p>
        )}
      </Card>
    );
  }

  return (
    <Card className="space-y-3" data-testid="collect-payment-due">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <IconCircle size="sm" tone="soft" color="orange" icon={<Hourglass />} />
          <p className="font-heading font-semibold text-text-primary">Waiting for payment</p>
        </div>
        <AmountText amount={payment.amount} size="lg" exact />
      </div>
      <p className="text-sm text-text-secondary">
        Your rider pays in her SheOut app, from her wallet or online. This updates on its own the moment she does.
      </p>
      <div className="flex items-start gap-2 rounded-card bg-background p-3 text-sm text-text-secondary">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <p>
          Please don&apos;t take cash or a transfer to your own account. Fares paid outside SheOut can&apos;t be
          recorded, and your trip won&apos;t count as paid.
        </p>
      </div>
    </Card>
  );
}
