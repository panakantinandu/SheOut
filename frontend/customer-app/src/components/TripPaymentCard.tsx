import { Banknote, CheckCircle2, CreditCard } from 'lucide-react';
import { useEffect, useState } from 'react';
import { AmountText, Button, Card, IconCircle, paymentMethodLabel } from '@sheout/design-system';
import { ApiError, paymentsApi, usersApi } from '../api/client';
import type { BookingSummary, PaymentSummary } from '../api/types';
import { openRazorpayCheckout } from '../lib/razorpayCheckout';

const POLL_INTERVAL_MS = 4000;

/**
 * How a finished trip gets paid: online through Razorpay Checkout, or cash
 * into the partner's hand, which she then confirms from her app.
 * <p>
 * Polls until the payment is captured, because either route can finish
 * somewhere else - the partner confirming cash, or Razorpay's webhook landing
 * after a Checkout the rider closed too early - and the rider should see it
 * turn to paid without refreshing.
 */
export function TripPaymentCard({ booking, onPaid }: { booking: BookingSummary; onPaid?: () => void }) {
  const [payment, setPayment] = useState<PaymentSummary | null>(null);
  const [paying, setPaying] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const captured = payment?.status === 'CAPTURED';

  useEffect(() => {
    if (captured) onPaid?.();
    // onPaid is a fresh closure on every parent render; firing once per capture is the point.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [captured]);

  useEffect(() => {
    if (captured) return;
    let cancelled = false;
    const load = () =>
      paymentsApi
        .getForBooking(booking.id)
        .then((p) => {
          if (!cancelled) setPayment(p);
        })
        // 404 is the moment between completion and the payment row being
        // written; anything else is transient too. Both: poll again.
        .catch(() => undefined);
    load();
    const timer = window.setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [booking.id, captured]);

  const handlePayOnline = async () => {
    setPaying(true);
    setMessage(null);
    try {
      const [details, contact] = await Promise.all([
        paymentsApi.getCheckout(booking.id),
        // A nicety, not a requirement: without it Checkout just asks.
        usersApi.getMyProfile().then((p) => p.phoneNumber ?? undefined).catch(() => undefined),
      ]);
      const outcome = await openRazorpayCheckout(
        details,
        `${booking.type === 'DELIVERY' ? 'Delivery' : 'Ride'} fare`,
        contact
      );
      if (outcome.kind === 'dismissed') return;
      if (outcome.kind === 'failed') {
        setMessage(`${outcome.message} Nothing was taken - you can try again or pay cash.`);
        return;
      }
      setPayment(await paymentsApi.verifyCheckout(booking.id, outcome.result));
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // Already paid - most likely the partner confirmed cash meanwhile.
        setPayment(await paymentsApi.getForBooking(booking.id).catch(() => payment));
      } else if (err instanceof ApiError && err.body?.error === 'PAYMENT_NOT_VERIFIED') {
        setMessage('We could not verify that payment. If money left your account, contact support with this trip.');
      } else if (err instanceof ApiError && (err.status === 502 || err.status >= 500)) {
        setMessage('Online payment is not available right now. Please try again in a minute, or pay your partner in cash.');
      } else {
        setMessage(err instanceof Error ? err.message : 'Online payment is not available right now. You can pay cash.');
      }
    } finally {
      setPaying(false);
    }
  };

  if (!payment) {
    return (
      <Card>
        <p className="text-sm text-text-secondary">Getting your fare ready...</p>
      </Card>
    );
  }

  if (captured) {
    return (
      <Card tone="success" className="flex items-center gap-3" data-testid="trip-payment-paid">
        <IconCircle size="md" tone="soft" color="green" icon={<CheckCircle2 />} />
        <div className="flex-1">
          <p className="font-heading font-semibold text-text-primary">Paid</p>
          <p className="text-sm text-text-secondary">
            {payment.method === 'CASH' ? 'Cash to your partner' : `Online · ${paymentMethodLabel(payment.method)}`}
          </p>
        </div>
        <AmountText amount={payment.amount} size="lg" exact />
      </Card>
    );
  }

  return (
    <Card data-testid="trip-payment-due">
      <div className="flex items-center justify-between">
        <p className="font-heading font-semibold text-text-primary">Pay for this trip</p>
        <AmountText amount={payment.amount} size="lg" exact />
      </div>
      <Button
        className="mt-3"
        fullWidth
        size="md"
        icon={<CreditCard className="h-4 w-4" />}
        disabled={paying}
        onClick={handlePayOnline}
      >
        {paying ? 'Opening payment...' : 'Pay online - UPI, card or netbanking'}
      </Button>
      <div className="mt-3 flex items-start gap-2 text-sm text-text-secondary">
        <Banknote className="mt-0.5 h-4 w-4 shrink-0" />
        <p>Paying cash? Hand the fare to your partner. She confirms it from her app and this updates on its own.</p>
      </div>
      {message && <p className="mt-2 text-sm text-danger">{message}</p>}
    </Card>
  );
}
