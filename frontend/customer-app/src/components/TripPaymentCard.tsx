import { CheckCircle2, CreditCard, ShieldCheck, Wallet as WalletIcon } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Button, Card, IconCircle, paymentMethodLabel } from '@sheout/design-system';
import { ApiError, paymentsApi, usersApi, walletApi } from '../api/client';
import type { BookingSummary, PaymentSummary, RiderWallet } from '../api/types';
import { openRazorpayCheckout } from '../lib/razorpayCheckout';
import { apiErrorText } from '../lib/apiErrors';
import { useTranslation } from '@sheout/design-system';
import { PromoFareLines } from './PromoFareLines';

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
  const { t } = useTranslation();
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
        setMessage(t('tripPay.lowBalance'));
      } else if (err instanceof ApiError && err.status === 409) {
        await refreshAfterConflict();
      } else {
        setMessage(apiErrorText(err, 'tripPay.walletError'));
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
        booking.type === 'DELIVERY' ? t('tripPay.deliveryFare') : t('tripPay.rideFare'),
        contact
      );
      if (outcome.kind === 'dismissed') return;
      if (outcome.kind === 'failed') {
        setMessage(t('tripPay.checkoutFailed', { reason: outcome.message }));
        return;
      }
      setPayment(await paymentsApi.verifyCheckout(booking.id, outcome.result));
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        await refreshAfterConflict();
      } else if (err instanceof ApiError && err.body?.error === 'PAYMENT_NOT_VERIFIED') {
        setMessage(t('apiError.PAYMENT_NOT_VERIFIED'));
      } else if (err instanceof ApiError && err.status >= 500) {
        setMessage(t('tripPay.onlineUnavailable'));
      } else {
        setMessage(apiErrorText(err, 'tripPay.onlineUnavailable'));
      }
    } finally {
      setPaying(null);
    }
  };

  if (!payment) {
    return (
      <Card>
        <p className="text-sm text-text-secondary">{t('tripPay.gettingReady')}</p>
      </Card>
    );
  }

  const promo = booking.promoDiscount > 0 ? (
    <PromoFareLines
      fare={booking.finalFare ?? booking.fareEstimate}
      discount={booking.promoDiscount}
      youPay={booking.amountDue}
      promotionName={booking.promotionName}
    />
  ) : null;

  if (paid) {
    return (
      <Card tone="success" className="space-y-3" data-testid="trip-payment-paid">
        <div className="flex items-center gap-3">
          <IconCircle size="md" tone="soft" color="green" icon={<CheckCircle2 />} />
          <div className="flex-1">
            <p className="font-heading font-semibold text-text-primary">{t('tripPay.paid')}</p>
            <p className="text-sm text-text-secondary">
              {payment.status === 'WAIVED'
                ? t('tripPay.settledEarlier')
                : payment.method === 'CASH'
                  ? t('tripPay.cashToPartner')
                  : payment.method === 'PROMO_CREDIT'
                    ? t('tripPay.coveredByPromo', { name: booking.promotionName ?? t('promo.offer') })
                    : paymentMethodLabel(payment.method)}
            </p>
          </div>
          <AmountText amount={payment.amount} size="lg" exact />
        </div>
        {promo}
      </Card>
    );
  }

  const balance = wallet?.balance ?? null;
  const enough = balance !== null && balance >= payment.amount;

  return (
    <Card data-testid="trip-payment-due" className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="font-heading font-semibold text-text-primary">{t('tripPay.payForTrip')}</p>
        <AmountText amount={payment.amount} size="lg" exact />
      </div>
      {promo}

      <Button
        fullWidth
        size="md"
        icon={<WalletIcon className="h-4 w-4" />}
        disabled={paying !== null || !enough}
        onClick={handlePayFromWallet}
        data-testid="pay-from-wallet"
      >
        {paying === 'wallet'
          ? t('tripPay.paying')
          : balance === null
            ? t('tripPay.payFromWallet')
            : t('tripPay.payFromWalletBalance', { amount: balance.toFixed(2) })}
      </Button>
      {balance !== null && !enough && (
        <button
          type="button"
          className="w-full text-center text-sm font-semibold text-primary"
          onClick={() => navigate('/wallet', { state: { returnTo: `/tracking/${booking.id}`, need: payment.amount - balance } })}
        >
          {t('tripPay.addToWallet', { amount: Math.ceil(payment.amount - balance) })}
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
        {paying === 'online' ? t('wallet.opening') : t('tripPay.payOnline')}
      </Button>

      <div className="flex items-start gap-2 text-sm text-text-secondary">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <p>
          {t('tripPay.appOnlyNote')}
        </p>
      </div>
      {message && <p className="text-sm text-danger">{message}</p>}
    </Card>
  );
}
