import { ReceiptText, SearchX } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AmountText,
  Card,
  DateRangeFields,
  ListEmptyState,
  ListFilterBar,
  LoadMore,
  SelectField,
  StatusBadge,
  TextField,
  TopHeader,
  paymentMethodLabel,
  paymentStatusLabel,
  endOfDayIso,
  startOfDayIso,
  usePagedList,
} from '@sheout/design-system';
import type { DateRangeValue, StatusTone } from '@sheout/design-system';
import { paymentsApi } from '../api/client';
import type { PaymentStatus } from '../api/types';

const TONES: Record<PaymentStatus, StatusTone> = {
  PENDING: 'warning',
  CAPTURED: 'success',
  FAILED: 'danger',
  REFUNDED: 'primary',
};

const STATUS_OPTIONS: { value: PaymentStatus; label: string }[] = [
  { value: 'CAPTURED', label: 'Paid' },
  { value: 'PENDING', label: 'Awaiting payment' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'REFUNDED', label: 'Refunded' },
];

/**
 * What the customer has actually been charged, paged and filtered.
 * <p>
 * This screen used to fetch every booking the customer had ever made and
 * then issue one payment request per booking, discarding every booking
 * without a payment row. An N+1 from the browser, growing with the whole
 * history, and impossible to page or filter. It now reads one paged
 * endpoint - see paymentsApi.search.
 * <p>
 * No search box. A payment has an amount, a method, a status and two
 * timestamps, and nothing worth typing at; a box that matched nothing would
 * be worse than none. Date, status and amount range are what someone
 * actually reaches for when hunting a charge they half-remember.
 */
export function PaymentMethods() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<PaymentStatus | ''>('');
  const [dates, setDates] = useState<DateRangeValue>({ from: '', to: '' });
  const [minAmount, setMinAmount] = useState('');
  const [maxAmount, setMaxAmount] = useState('');

  const fetchPage = useCallback(
    (page: number) =>
      paymentsApi.search({
        page,
        status: status || undefined,
        from: startOfDayIso(dates.from),
        to: endOfDayIso(dates.to),
        // An empty box is no bound at all, not zero - a min of 0 would read
        // as a filter while changing nothing.
        minAmount: minAmount ? Number(minAmount) : undefined,
        maxAmount: maxAmount ? Number(maxAmount) : undefined,
      }),
    [status, dates.from, dates.to, minAmount, maxAmount]
  );

  const list = usePagedList(fetchPage, [status, dates.from, dates.to, minAmount, maxAmount], {
    debounceMs: 400,
  });

  const activeFilters = useMemo(
    () => [status, dates.from, dates.to, minAmount, maxAmount].filter(Boolean).length,
    [status, dates.from, dates.to, minAmount, maxAmount]
  );

  function clearAll() {
    setStatus('');
    setDates({ from: '', to: '' });
    setMinAmount('');
    setMaxAmount('');
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Payment History" onBack={() => navigate('/profile')} />

      <Card>
        <p className="text-sm text-text-primary">Payments are taken per trip via UPI or cash.</p>
        <p className="mt-1 text-xs text-text-secondary">
          No card or UPI ID is stored on your account - there is nothing saved to manage here, so this shows what you
          have actually been charged.
        </p>
      </Card>

      <ListFilterBar activeCount={activeFilters} onClearAll={clearAll}>
        <SelectField
          label="Status"
          placeholder="Any status"
          value={status}
          onChange={(e) => setStatus(e.target.value as PaymentStatus | '')}
          options={STATUS_OPTIONS}
        />
        <DateRangeFields value={dates} onChange={setDates} />
        <div>
          <span className="mb-1.5 block text-sm font-medium text-text-primary">Amount range</span>
          <div className="flex items-center gap-2">
            <div className="min-w-0 flex-1">
              <TextField
                type="number"
                inputMode="numeric"
                aria-label="Minimum amount"
                placeholder="Min ₹"
                value={minAmount}
                min={0}
                onChange={(e) => setMinAmount(e.target.value)}
              />
            </div>
            <span className="shrink-0 text-sm text-text-secondary">to</span>
            <div className="min-w-0 flex-1">
              <TextField
                type="number"
                inputMode="numeric"
                aria-label="Maximum amount"
                placeholder="Max ₹"
                value={maxAmount}
                min={0}
                onChange={(e) => setMaxAmount(e.target.value)}
              />
            </div>
          </div>
        </div>
      </ListFilterBar>

      {list.error && <p className="text-sm text-danger">{list.error}</p>}
      {list.loading && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {!list.loading && list.items.length === 0 && !list.error && (
        activeFilters > 0 ? (
          <ListEmptyState
            icon={<SearchX />}
            title="No results match your filters"
            message="Your payments are still here. Try a wider date range, a different status, or clear the filters."
            action={{ label: 'Clear filters', onClick: clearAll }}
          />
        ) : (
          <ListEmptyState
            icon={<ReceiptText />}
            title="No payments yet"
            message="Payments appear here once a trip is completed."
          />
        )
      )}

      <div className="space-y-3">
        {list.items.map((payment) => (
          <Card key={payment.id} className="flex items-center justify-between">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-text-primary">{paymentMethodLabel(payment.method)}</p>
              <p className="text-xs text-text-secondary">{new Date(payment.createdAt).toLocaleString()}</p>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              <StatusBadge tone={TONES[payment.status]}>{paymentStatusLabel(payment.status)}</StatusBadge>
              <AmountText amount={payment.amount} />
            </div>
          </Card>
        ))}
      </div>

      <LoadMore
        shown={list.items.length}
        total={list.total}
        hasMore={list.hasMore}
        loading={list.loadingMore}
        onLoadMore={list.loadMore}
      />
    </div>
  );
}
