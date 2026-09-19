import { i18next } from '@sheout/design-system';
import { Landmark, Wallet } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  ConfirmDialog,
  IconCircle,
  SkeletonCard,
  StatusBadge,
  TextField,
  TopHeader,
} from '@sheout/design-system';
import { ApiError, payoutsApi } from '../api/client';
import type { PayoutOverview, SavePayoutAccount } from '../api/types';
import { useTranslation } from '@sheout/design-system';

const MIN_PAYOUT = 1;

const EMPTY_FORM: SavePayoutAccount = { accountHolderName: '', accountNumber: '', ifsc: '', upiVpa: '' };

/** Blank fields go as null, so "no bank account" is not sent as an empty one. */
function cleaned(form: SavePayoutAccount, keepSavedBankAccount: boolean): SavePayoutAccount {
  const value = (s: string | null) => (s && s.trim() ? s.trim() : null);
  if (keepSavedBankAccount) {
    return { accountHolderName: null, accountNumber: null, ifsc: null, upiVpa: value(form.upiVpa), keepSavedBankAccount: true };
  }
  return {
    accountHolderName: value(form.accountHolderName),
    accountNumber: value(form.accountNumber)?.replace(/\s+/g, '') ?? null,
    ifsc: value(form.ifsc)?.toUpperCase() ?? null,
    upiVpa: value(form.upiVpa),
  };
}

type BankChoice = 'KEEP' | 'CHANGE' | 'REMOVE';

/**
 * The server's rules (PayoutService), repeated here only to say WHICH field
 * is wrong - the server answers every one of these with a single message.
 * The server is still the rule; this is the explanation.
 */
function detailsProblem(body: SavePayoutAccount): string | null {
  const anyBank = body.accountHolderName || body.accountNumber || body.ifsc;
  if (!body.keepSavedBankAccount && anyBank) {
    if (!body.accountHolderName || !/^[\p{L} .'-]{2,100}$/u.test(body.accountHolderName)) {
      return i18next.t('payouts.problem.holder');
    }
    if (!body.accountNumber || !/^\d{9,18}$/.test(body.accountNumber)) {
      return i18next.t('payouts.problem.number');
    }
    if (!body.ifsc || !/^[A-Z]{4}0[A-Z0-9]{6}$/.test(body.ifsc)) {
      return i18next.t('payouts.problem.ifsc');
    }
  }
  if (body.upiVpa && !/^[a-zA-Z0-9.\-_]{2,64}@[a-zA-Z][a-zA-Z0-9]{1,34}$/.test(body.upiVpa)) {
    return i18next.t('payouts.problem.upi');
  }
  return null;
}

const RUPEES = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Exact to the paisa. AmountText rounds to whole rupees, which suits a fare
 * but not a balance or a payout: ₹100.50 requested must not read as ₹101.
 */
function rupees(amount: number): string {
  return `₹${RUPEES.format(amount)}`;
}

/**
 * Her wallet, where she is paid, and her payout requests.
 * <p>
 * Payouts are sent by hand by SheOut's operations team, not by an automated
 * transfer, so a request says "pending" until someone has actually sent the
 * money and recorded the bank or UPI reference - which is then shown here.
 * <p>
 * The account number is only ever shown masked. It is typed in full once,
 * and never sent back to the phone.
 */
export function Payouts() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [overview, setOverview] = useState<PayoutOverview | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<SavePayoutAccount>(EMPTY_FORM);
  const [bankChoice, setBankChoice] = useState<BankChoice>('CHANGE');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const [amount, setAmount] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [requesting, setRequesting] = useState(false);
  const [requestError, setRequestError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const fetched = await payoutsApi.overview();
      setOverview(fetched);
      setLoadError(null);
      return fetched;
    } catch (err) {
      setLoadError(err instanceof ApiError ? err.message : t('payouts.loadError'));
      return null;
    }
  }, []);

  useEffect(() => {
    load().then((fetched) => {
      if (fetched && !fetched.account) setEditing(true);
      if (fetched && fetched.wallet.availableBalance >= MIN_PAYOUT) {
        setAmount(fetched.wallet.availableBalance.toFixed(2));
      }
    });
  }, [load]);

  function startEditing() {
    // The saved account number is masked, so it is never pre-filled. By
    // default the saved bank account is kept and only the UPI ID is open;
    // changing the bank account means typing all of it again.
    setForm({
      accountHolderName: overview?.account?.accountHolderName ?? '',
      accountNumber: '',
      ifsc: overview?.account?.ifsc ?? '',
      upiVpa: overview?.account?.upiVpa ?? '',
    });
    setBankChoice(overview?.account?.accountNumberMasked ? 'KEEP' : 'CHANGE');
    setSaveError(null);
    setEditing(true);
  }

  async function handleSave() {
    const body =
      bankChoice === 'REMOVE'
        ? { accountHolderName: null, accountNumber: null, ifsc: null, upiVpa: cleaned(form, false).upiVpa }
        : cleaned(form, bankChoice === 'KEEP');
    if (!body.keepSavedBankAccount && !body.accountNumber && !body.accountHolderName && !body.ifsc && !body.upiVpa) {
      setSaveError(t('payouts.needDetails'));
      return;
    }
    const problem = detailsProblem(body);
    if (problem) {
      setSaveError(problem);
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      await payoutsApi.saveAccount(body);
      setEditing(false);
      await load();
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : t('payouts.saveError'));
    } finally {
      setSaving(false);
    }
  }

  const parsedAmount = Number(amount);
  const available = overview?.wallet.availableBalance ?? 0;
  const amountValid =
    /^\d+(\.\d{1,2})?$/.test(amount.trim()) && parsedAmount >= MIN_PAYOUT && parsedAmount <= available;

  async function handleRequest() {
    setRequesting(true);
    setRequestError(null);
    try {
      await payoutsApi.requestPayout(parsedAmount);
      setConfirming(false);
      setAmount('');
      await load();
    } catch (err) {
      setConfirming(false);
      setRequestError(err instanceof ApiError ? err.message : t('payouts.requestError'));
    } finally {
      setRequesting(false);
    }
  }

  const wallet = overview?.wallet;
  const account = overview?.account;

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('payouts.title')} onBack={() => navigate(-1)} />

      {loadError && <p className="text-sm text-danger">{loadError}</p>}
      {!overview && !loadError && <SkeletonCard lines={4} label={t('payouts.loading')} />}

      {wallet && (
        <>
          <Card variant="primary" className="space-y-3" data-testid="wallet-card">
            <div>
              <p className="text-sm opacity-90">{t('payouts.available')}</p>
              <p className="font-heading text-3xl font-bold" data-testid="wallet-available">
                {wallet.availableBalance < 0 ? '-' : ''}
                {rupees(Math.abs(wallet.availableBalance))}
              </p>
            </div>
            <div className="grid grid-cols-2 gap-2 text-sm">
              <div>
                <p className="opacity-80">{t('payouts.earned')}</p>
                <p className="font-semibold">{rupees(wallet.totalEarned)}</p>
              </div>
              <div>
                <p className="opacity-80">{t('payouts.cashCollected')}</p>
                <p className="font-semibold">{rupees(wallet.cashCollected)}</p>
              </div>
              <div>
                <p className="opacity-80">{t('payouts.paidOut')}</p>
                <p className="font-semibold">{rupees(wallet.totalPaidOut)}</p>
              </div>
              <div>
                <p className="opacity-80">{t('payouts.beingPaid')}</p>
                <p className="font-semibold">{rupees(wallet.pendingPayouts)}</p>
              </div>
            </div>
          </Card>

          {wallet.availableBalance < 0 && (
            <Card tone="warning">
              <p className="text-sm text-text-primary">
                {t('payouts.negative')}
              </p>
            </Card>
          )}

          <Card className="space-y-3">
            <div className="flex items-center gap-3">
              <IconCircle tone="soft" icon={<Landmark />} />
              <div className="flex-1">
                <p className="font-heading font-semibold text-text-primary">{t('payouts.whereTitle')}</p>
                <p className="text-xs text-text-secondary">{t('payouts.whereSub')}</p>
              </div>
              {account && !editing && (
                <Button size="md" variant="secondary" onClick={startEditing}>
                  {t('payouts.edit')}
                </Button>
              )}
            </div>

            {account && !editing && (
              <div className="space-y-1 text-sm" data-testid="payout-account">
                {account.accountNumberMasked && (
                  <p className="text-text-primary">
                    {account.accountHolderName} · A/c {account.accountNumberMasked} · {account.ifsc}
                  </p>
                )}
                {account.upiVpa && <p className="text-text-primary">UPI {account.upiVpa}</p>}
              </div>
            )}

            {editing && (
              <div className="space-y-3">
                {account?.accountNumberMasked && (
                  <div className="space-y-2" role="radiogroup" aria-label={t('payouts.bankAccount')}>
                    <p className="text-sm font-medium text-text-primary">
                      {t('payouts.onFile', { masked: account.accountNumberMasked, ifsc: account.ifsc })}
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {(
                        [
                          ['KEEP', t('payouts.keep')],
                          ['CHANGE', t('payouts.change')],
                          ['REMOVE', t('payouts.remove')],
                        ] as [BankChoice, string][]
                      ).map(([choice, label]) => (
                        <button
                          key={choice}
                          type="button"
                          role="radio"
                          aria-checked={bankChoice === choice}
                          onClick={() => setBankChoice(choice)}
                          className={
                            bankChoice === choice
                              ? 'rounded-full bg-primary px-4 py-1.5 text-xs font-semibold text-text-inverse'
                              : 'rounded-full border border-border px-4 py-1.5 text-xs font-medium text-text-primary'
                          }
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
                {bankChoice === 'CHANGE' && (
                  <>
                    <TextField
                      label={t('payouts.holderName')}
                      value={form.accountHolderName ?? ''}
                      autoComplete="name"
                      maxLength={100}
                      onChange={(e) => setForm({ ...form, accountHolderName: e.target.value })}
                    />
                    <TextField
                      label={t('payouts.accountNumber')}
                      value={form.accountNumber ?? ''}
                      inputMode="numeric"
                      autoComplete="off"
                      maxLength={18}
                      placeholder={t('payouts.accountNumberPlaceholder')}
                      onChange={(e) => setForm({ ...form, accountNumber: e.target.value.replace(/\D/g, '') })}
                    />
                    <TextField
                      label="IFSC"
                      value={form.ifsc ?? ''}
                      autoComplete="off"
                      maxLength={11}
                      placeholder={t('payouts.ifscPlaceholder')}
                      onChange={(e) => setForm({ ...form, ifsc: e.target.value.toUpperCase() })}
                    />
                  </>
                )}
                <TextField
                  label="UPI ID"
                  value={form.upiVpa ?? ''}
                  autoComplete="off"
                  maxLength={100}
                  placeholder={t('payouts.upiPlaceholder')}
                  onChange={(e) => setForm({ ...form, upiVpa: e.target.value })}
                />
                <p className="text-xs text-text-secondary">
                  {t('payouts.ownNameNote')}
                </p>
                {/* Each request keeps the details it was made with, so a change
                    here - by her, or by someone else holding her phone - cannot
                    redirect money already asked for. She should know that. */}
                {overview?.requests.some((r) => r.status === 'PENDING') && (
                  <p className="text-xs font-medium text-text-primary">
                    {t('payouts.pendingNote')}
                  </p>
                )}
                {saveError && <p className="text-sm text-danger">{saveError}</p>}
                <div className="flex gap-2">
                  {account && (
                    <Button fullWidth size="md" variant="secondary" disabled={saving} onClick={() => setEditing(false)}>
                      {t('common.cancel')}
                    </Button>
                  )}
                  <Button fullWidth size="md" disabled={saving} onClick={handleSave}>
                    {saving ? t('common.saving') : t('payouts.saveDetails')}
                  </Button>
                </div>
              </div>
            )}
          </Card>

          <Card className="space-y-3">
            <div className="flex items-center gap-3">
              <IconCircle tone="soft" color="green" icon={<Wallet />} />
              <p className="flex-1 font-heading font-semibold text-text-primary">{t('payouts.requestTitle')}</p>
            </div>
            {!account ? (
              <p className="text-sm text-text-secondary">{t('payouts.saveFirst')}</p>
            ) : available < MIN_PAYOUT ? (
              <p className="text-sm text-text-secondary">{t('payouts.nothingAvailable')}</p>
            ) : (
              <>
                <TextField
                  label={t('payouts.amountUpTo', { amount: rupees(available) })}
                  value={amount}
                  inputMode="decimal"
                  onChange={(e) => {
                    setAmount(e.target.value);
                    setRequestError(null);
                  }}
                />
                <Button fullWidth size="md" disabled={!amountValid || requesting} onClick={() => setConfirming(true)}>
                  {t('payouts.request')}
                </Button>
                <p className="text-xs text-text-secondary">
                  {t('payouts.requestNote')}
                </p>
              </>
            )}
            {requestError && <p className="text-sm text-danger">{requestError}</p>}
          </Card>

          <div>
            <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('payouts.history')}</h2>
            {overview.requests.length === 0 ? (
              <p className="text-center text-sm text-text-secondary">
                {t('payouts.historyEmpty')}
              </p>
            ) : (
              <Card className="divide-y divide-border p-0" data-testid="payout-history">
                {overview.requests.map((r) => (
                  <div key={r.id} className="flex items-center gap-3 p-4">
                    <IconCircle tone="soft" size="sm" color={r.status === 'PAID' ? 'green' : 'orange'} icon={<Landmark />} />
                    <div className="min-w-0 flex-1">
                      <p className="break-words text-sm font-medium text-text-primary">{r.destination}</p>
                      <p className="text-xs text-text-secondary">
                        {t('payouts.requestedAt', { date: new Date(r.requestedAt).toLocaleString() })}
                        {r.paidAt ? ` · ${t('payouts.paidAt', { date: new Date(r.paidAt).toLocaleDateString() })}` : ''}
                      </p>
                      {r.paymentReference && (
                        <p className="truncate text-xs text-text-secondary">{t('payouts.reference', { ref: r.paymentReference })}</p>
                      )}
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      <span className="font-heading font-semibold tabular-nums text-text-primary">{rupees(r.amount)}</span>
                      <StatusBadge tone={r.status === 'PAID' ? 'success' : 'warning'}>
                        {r.status === 'PAID' ? t('payouts.statusPaid') : t('payouts.statusPending')}
                      </StatusBadge>
                    </div>
                  </div>
                ))}
              </Card>
            )}
          </div>
        </>
      )}

      <ConfirmDialog
        open={confirming}
        title={t('payouts.confirmTitle', { amount: amountValid ? rupees(parsedAmount) : '' })}
        message={t('payouts.confirmMessage', {
          destination: account?.accountNumberMasked
            ? t('payouts.toBank', { masked: account.accountNumberMasked }) + (account.upiVpa ? ` / UPI ${account.upiVpa}` : '')
            : `UPI ${account?.upiVpa ?? ''}`,
        })}
        confirmLabel={requesting ? t('payouts.requesting') : t('payouts.request')}
        onConfirm={handleRequest}
        onCancel={() => setConfirming(false)}
      />
    </div>
  );
}
