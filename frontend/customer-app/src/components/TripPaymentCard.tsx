import { CheckCircle2, CreditCard, ShieldCheck, Wallet as WalletIcon } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Button, Card, IconCircle, paymentMethodLabel } from '@sheout/design-system';
import { ApiError, paymentsApi, usersApi, walletApi } from '../api/client';
import type { BookingSummary, PaymentSummary, RiderWallet } from '../api/types';
import { openRazorpayCheckout } from '../lib/razorpayCheckout';

const POLL_INTERVAL_MS = 4000;

/**
 * How a finished trip gets paid - always through SheOut, from her wallet or
 * online, and always into her partner's SheOut wallet.
 * <p>
 * There is no cash option. A fare handed over in cash, or sent to a partner's
 * own account, leaves no record: nobody can show it was paid, the partner
 * cannot be protected if it was not, and the rider cannot be refunded if
 * something went wrong. Until this trip is paid she cannot book another.
 * <p>
 * Polls until paid, because the capture can land elsewhere - Razorpay's
 * webhook after a Checkout she closed too early.
 */
export function TripPaymentCard({ booking, onPaid }: { booking: BookingSummary; onPaid?: () => void }) {
  const navigate = useNavigate();
  const [payment, setPayment] = useState<PaymentSummary | null>(null);
  const [wallet, setWallet] = useState<RiderWallet | null>(null);
  const [paying, setPaying] = useState<'wallet' | 'online' | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const paid = payment?.status === 'CAPTURED' || payment?.status === 'WAIVED';

  useEffect(() => {
    if (paid) onPaid?.();
    // onPaid is a fresh closure on every parent render; firing once per capture is the point.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paid]);

  const loadWallet = useCallback(() => {
    walletApi.get().then(setWallet).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (paid) return;
    loadWallet();
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
  }, [booking.id, paid, loadWallet]);

  const refreshAfterConflict = async () => {
    setPayment(await paymentsApi.getForBooking(booking.id).catch(() => payment));
  };

  const handlePayFromWallet = async () => {
    setPaying('wallet');
    setMessage(null);
    try {
      setPayment(await paymentsApi.payFromWallet(booking.id));
      loadWallet();
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'INSUFFICIENT_BALANCE') {
        loadWallet();
        setMessage('Your wallet balance is too low for this fare. Add money, or pay online.');
      } else if (err instanceof ApiError && err.status === 409) {
        await refreshAfterConflict();
      } else {
        setMessage(err instanceof ApiError ? err.message : 'Could not pay from your wallet. Please try again.');
      }
    } finally {
      setPaying(null);
    }
  };

  const handlePayOnline = async () => {
    setPaying('online');
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
        setMessage(`${outcome.message} Nothing was taken - you can try again.`);
        return;
      }
      setPayment(await paymentsApi.verifyCheckout(booking.id, outcome.result));
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        await refreshAfterConflict();
      } else if (err instanceof ApiError && err.body?.error === 'PAYMENT_NOT_VERIFIED') {
        setMessage('We could not verify that payment. If money left your account, contact support with this trip.');
      } else if (err instanceof ApiError && err.status >= 500) {
        setMessage('Online payment is not available right now. Please try again in a minute, or pay from your wallet.');
      } else {
        setMessage(err instanceof Error ? err.message : 'Online payment is not available right now.');
      }
    } finally {
      setPaying(null);
    }
  };

  if (!payment) {
    return (
      <Card>
        <p className="text-sm text-text-secondary">Getting your fare ready...</p>
      </Card>
    );
  }

  if (paid) {
    return (
      <Card tone="success" className="flex items-center gap-3" data-testid="trip-payment-paid">
        <IconCircle size="md" tone="soft" color="green" icon={<CheckCircle2 />} />
        <div className="flex-1">
          <p className="font-heading font-semibold text-text-primary">Paid</p>
          <p className="text-sm text-text-secondary">
            {payment.status === 'WAIVED'
              ? 'Settled earlier'
              : payment.method === 'CASH'
                ? 'Cash to your partner'
                : paymentMethodLabel(payment.method)}
          </p>
        </div>
        <AmountText amount={payment.amount} size="lg" exact />
      </Card>
    );
  }

  const balance = wallet?.balance ?? null;
  const enough = balance !== null && balance >= payment.amount;

  return (
    <Card data-testid="trip-payment-due" className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="font-heading font-semibold text-text-primary">Pay for this trip</p>
        <AmountText amount={payment.amount} size="lg" exact />
      </div>

      <Button
        fullWidth
        size="md"
        icon={<WalletIcon className="h-4 w-4" />}
        disabled={paying !== null || !enough}
        onClick={handlePayFromWallet}
        data-testid="pay-from-wallet"
      >
        {paying === 'wallet'
          ? 'Paying...'
          : balance === null
            ? 'Pay from SheOut wallet'
            : `Pay from wallet (balance ₹${balance.toFixed(2)})`}
      </Button>
      {balance !== null && !enough && (
        <button
          type="button"
          className="w-full text-center text-sm font-semibold text-primary"
          onClick={() => navigate('/wallet', { state: { returnTo: `/tracking/${booking.id}`, need: payment.amount - balance } })}
        >
          Add ₹{Math.ceil(payment.amount - balance)} or more to your wallet
        </button>
      )}

      <Button
        fullWidth
        size="md"
        variant="secondary"
        icon={<CreditCard className="h-4 w-4" />}
        disabled={paying !== null}
        onClick={handlePayOnline}
      >
        {paying === 'online' ? 'Opening payment...' : 'Pay online - UPI, card or netbanking'}
      </Button>

      <div className="flex items-start gap-2 text-sm text-text-secondary">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <p>
          Pay only in the app - never in cash or to your partner&apos;s own account. Your fare goes to her SheOut wallet,
          and you can book your next trip once it is paid.
        </p>
      </div>
      {message && <p className="text-sm text-danger">{message}</p>}
    </Card>
  );
}
