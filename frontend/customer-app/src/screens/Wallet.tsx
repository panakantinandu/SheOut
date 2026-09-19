import { ArrowDownLeft, Bike, PlusCircle, Receipt, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  AmountText,
  Button,
  Card,
  IconCircle,
  ListEmptyState,
  LoadMore,
  PullToRefresh,
  SkeletonCard,
  SkeletonList,
  TextField,
  TopHeader,
  usePagedList,
} from '@sheout/design-system';
import { ApiError, usersApi, walletApi } from '../api/client';
import type { RiderWallet, RiderWalletEntry } from '../api/types';
import { openRazorpayCheckout } from '../lib/razorpayCheckout';

const QUICK_AMOUNTS = [100, 200, 500, 1000];

/** Sent by the trip payment card when her balance is short, so the top-up returns her to the trip. */
interface WalletRouteState {
  returnTo?: string;
  need?: number;
}

/**
 * Her SheOut wallet: a balance she adds to online and spends on her own
 * trips.
 * <p>
 * There is no "Send money". A balance that can be passed to other people is
 * a licensed payment wallet under RBI rules; one that only pays for your own
 * SheOut trips is not. It also closes the obvious way to pay a partner off the
 * books - every rupee here reaches a partner only as the fare for a trip she
 * drove, recorded against that trip.
 */
export function Wallet() {
  const navigate = useNavigate();
  const location = useLocation();
  const routeState = (location.state as WalletRouteState | null) ?? {};

  const [wallet, setWallet] = useState<RiderWallet | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [adding, setAdding] = useState(Boolean(routeState.need));
  const [amount, setAmount] = useState(() =>
    routeState.need ? String(Math.max(Math.ceil(routeState.need), 10)) : ''
  );
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: 'danger' | 'success'; text: string } | null>(null);
  const amountInput = useRef<HTMLInputElement>(null);

  const loadWallet = useCallback(
    () =>
      walletApi
        .get()
        .then((w) => {
          setWallet(w);
          setLoadError(null);
        })
        .catch((err) => setLoadError(err instanceof ApiError ? err.message : 'Could not load your wallet.')),
    []
  );

  const list = usePagedList<RiderWalletEntry>((page) => walletApi.transactions({ page, pageSize: 20 }), []);

  useEffect(() => {
    void loadWallet();
  }, [loadWallet]);

  useEffect(() => {
    if (adding) amountInput.current?.focus();
  }, [adding]);

  const refresh = async () => {
    await loadWallet();
    list.reload();
  };

  const parsed = Number(amount);
  const amountValid =
    wallet !== null &&
    amount.trim() !== '' &&
    Number.isFinite(parsed) &&
    /^\d+(\.\d{1,2})?$/.test(amount.trim()) &&
    parsed >= wallet.minTopup &&
    parsed <= wallet.maxTopup &&
    wallet.balance + parsed <= wallet.maxBalance;

  const amountHint = (() => {
    if (!wallet) return undefined;
    if (amount.trim() === '' || amountValid) return undefined;
    if (wallet.balance + parsed > wallet.maxBalance) {
      return `Your wallet can hold up to ₹${wallet.maxBalance}. You can add up to ₹${Math.max(0, wallet.maxBalance - wallet.balance).toFixed(0)}.`;
    }
    return `Enter an amount from ₹${wallet.minTopup} to ₹${wallet.maxTopup}.`;
  })();

  const handleAddMoney = async () => {
    if (!amountValid) return;
    setBusy(true);
    setMessage(null);
    try {
      const [checkout, contact] = await Promise.all([
        walletApi.startTopup(parsed),
        usersApi.getMyProfile().then((p) => p.phoneNumber ?? undefined).catch(() => undefined),
      ]);
      const outcome = await openRazorpayCheckout(checkout, 'Add money to SheOut wallet', contact);
      if (outcome.kind === 'dismissed') return;
      if (outcome.kind === 'failed') {
        setMessage({ tone: 'danger', text: `${outcome.message} Nothing was added - you can try again.` });
        return;
      }
      const updated = await walletApi.verifyTopup(checkout.topupId, outcome.result);
      setWallet(updated);
      list.reload();
      setAdding(false);
      setAmount('');
      setMessage({ tone: 'success', text: `₹${parsed.toFixed(2)} added to your wallet.` });
      // She came here to cover a fare - take her back to it.
      if (routeState.returnTo) {
        navigate(routeState.returnTo, { replace: true });
      }
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'PAYMENT_NOT_VERIFIED') {
        setMessage({
          tone: 'danger',
          text: 'We could not verify that payment. If money left your account, it will be added shortly or refunded - contact support if not.',
        });
      } else {
        setMessage({
          tone: 'danger',
          text: err instanceof ApiError ? err.message : 'Could not add money right now. Please try again.',
        });
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <PullToRefresh onRefresh={refresh} disabled={busy} className="space-y-6">
      <TopHeader
        variant="back"
        title="Wallet"
        onBack={() => (routeState.returnTo ? navigate(routeState.returnTo) : navigate('/home'))}
      />

      {loadError && !wallet && <p className="text-sm text-danger">{loadError}</p>}
      {!wallet && !loadError && <SkeletonCard lines={2} label="Loading your wallet" />}

      {wallet && (
        <Card variant="primary">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm opacity-90">SheOut wallet balance</p>
              <AmountText amount={wallet.balance} size="lg" tone="inverse" exact animate />
            </div>
            <button
              type="button"
              className="shrink-0 rounded-full bg-surface px-4 py-2 text-sm font-semibold text-primary"
              onClick={() => setAdding(true)}
              data-testid="wallet-add-money"
            >
              Add Money
            </button>
          </div>
        </Card>
      )}

      {routeState.need && wallet && (
        <Card tone="warning">
          <p className="text-sm text-text-primary">
            Add at least ₹{Math.ceil(routeState.need)} to pay for your trip. You&apos;ll go straight back to it.
          </p>
        </Card>
      )}

      {adding && wallet && (
        <Card className="space-y-3" data-testid="wallet-add-panel">
          <p className="font-heading font-semibold text-text-primary">Add money</p>
          <div className="flex flex-wrap gap-2">
            {QUICK_AMOUNTS.filter((q) => wallet.balance + q <= wallet.maxBalance).map((q) => (
              <button
                key={q}
                type="button"
                onClick={() => setAmount(String(q))}
                className={
                  amount === String(q)
                    ? 'rounded-full bg-primary px-4 py-1.5 text-sm font-semibold text-text-inverse'
                    : 'rounded-full border border-border px-4 py-1.5 text-sm font-medium text-text-secondary'
                }
              >
                ₹{q}
              </button>
            ))}
          </div>
          <TextField
            ref={amountInput}
            label="Amount (₹)"
            inputMode="decimal"
            placeholder={`₹${wallet.minTopup} to ₹${wallet.maxTopup}`}
            value={amount}
            onChange={(e) => setAmount(e.target.value.replace(/[^\d.]/g, ''))}
            error={amountHint}
            data-testid="wallet-amount"
          />
          <div className="flex gap-2">
            <Button
              variant="secondary"
              className="flex-1"
              disabled={busy}
              onClick={() => {
                setAdding(false);
                setMessage(null);
              }}
            >
              Cancel
            </Button>
            <Button className="flex-1" disabled={!amountValid || busy} onClick={handleAddMoney} data-testid="wallet-pay">
              {busy ? 'Opening payment...' : amountValid ? `Add ₹${parsed.toFixed(0)}` : 'Add'}
            </Button>
          </div>
          <p className="text-xs text-text-secondary">
            Paid securely through Razorpay by UPI, card or netbanking. Money is added once your bank confirms it.
          </p>
        </Card>
      )}

      {message && (
        <p className={`text-sm font-medium ${message.tone === 'success' ? 'text-success' : 'text-danger'}`}>
          {message.text}
        </p>
      )}

      <div className="flex justify-around">
        <button type="button" className="flex flex-col items-center gap-1.5" onClick={() => setAdding(true)}>
          <IconCircle tone="soft" icon={<PlusCircle />} />
          <span className="text-xs font-medium text-text-primary">Add Money</span>
        </button>
        <button type="button" className="flex flex-col items-center gap-1.5" onClick={() => navigate('/bookings')}>
          <IconCircle tone="soft" color="green" icon={<Bike />} />
          <span className="text-xs font-medium text-text-primary">Pay a Trip</span>
        </button>
        <button type="button" className="flex flex-col items-center gap-1.5" onClick={() => navigate('/profile/payments')}>
          <IconCircle tone="soft" color="orange" icon={<Receipt />} />
          <span className="text-xs font-medium text-text-primary">Trip Payments</span>
        </button>
      </div>

      <div className="flex items-start gap-2 text-xs text-text-secondary">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <p>
          Your wallet pays only for your own SheOut trips, and each fare goes to your partner&apos;s SheOut wallet. It
          can&apos;t be sent to other people or withdrawn.
        </p>
      </div>

      <div className="space-y-3">
        <h2 className="font-heading text-base font-semibold text-text-primary">Wallet activity</h2>
        {list.error && <p className="text-sm text-danger">{list.error}</p>}
        {list.loading && <SkeletonList rows={3} label="Loading your wallet activity" />}
        {!list.loading && !list.error && list.items.length === 0 && (
          <ListEmptyState
            illustrated
            icon={<Receipt />}
            title="No activity yet"
            message="Money you add and trips you pay from your wallet appear here."
          />
        )}
        {list.items.length > 0 && (
          <Card className="divide-y divide-border p-0" data-testid="wallet-activity">
            {list.items.map((entry) => (
              <div
                key={entry.id}
                className={`flex items-center gap-3 p-4 ${entry.bookingId ? 'cursor-pointer' : ''}`}
                onClick={entry.bookingId ? () => navigate(`/tracking/${entry.bookingId}`) : undefined}
              >
                <IconCircle
                  tone="soft"
                  size="sm"
                  color={entry.type === 'TOPUP' ? 'green' : undefined}
                  icon={entry.type === 'TOPUP' ? <ArrowDownLeft /> : <Bike />}
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-text-primary">
                    {entry.type === 'TOPUP' ? 'Money added' : 'Trip payment'}
                  </p>
                  <p className="text-xs text-text-secondary">
                    {new Date(entry.createdAt).toLocaleString(undefined, {
                      day: 'numeric',
                      month: 'short',
                      hour: 'numeric',
                      minute: '2-digit',
                    })}{' '}
                    · Balance ₹{entry.balanceAfter.toFixed(2)}
                  </p>
                </div>
                <AmountText amount={Math.abs(entry.amount)} sign={entry.amount >= 0 ? 'positive' : 'negative'} exact />
              </div>
            ))}
          </Card>
        )}
        <LoadMore
          shown={list.items.length}
          total={list.total}
          hasMore={list.hasMore}
          loading={list.loadingMore}
          onLoadMore={list.loadMore}
        />
      </div>
    </PullToRefresh>
  );
}
