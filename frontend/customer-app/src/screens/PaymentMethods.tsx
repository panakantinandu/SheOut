import { ReceiptText, SearchX } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AmountText,
  Card,
  DateRangeFields,
  endOfDayIso,
  ListEmptyState,
  ListFilterBar,
  LoadMore,
  paymentMethodLabel,
  paymentStatusLabel,
  SelectField,
  SkeletonList,
  startOfDayIso,
  StatusBadge,
  TextField,
  TopHeader,
  usePagedList,
} from '@sheout/design-system';
import type { DateRangeValue, StatusTone } from '@sheout/design-system';
import { paymentsApi } from '../api/client';
import type { PaymentStatus } from '../api/types';
import { useTranslation } from '@sheout/design-system';

const TONES: Record<PaymentStatus, StatusTone> = {
  PENDING: 'warning',
  CAPTURED: 'success',
  FAILED: 'danger',
  REFUNDED: 'primary',
  WAIVED: 'primary',
};

const STATUS_VALUES: PaymentStatus[] = ['CAPTURED', 'PENDING', 'FAILED', 'REFUNDED', 'WAIVED'];

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
  const { t } = useTranslation();
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
      <TopHeader variant="back" title={t('payments.history')} onBack={() => navigate(-1)} />

      <Card>
        <p className="text-sm text-text-primary">{t('payments.howPaid')}</p>
        <p className="mt-1 text-xs text-text-secondary">
          {t('payments.nothingStored')}
        </p>
      </Card>

      <ListFilterBar activeCount={activeFilters} onClearAll={clearAll}>
        <SelectField
          label={t('payments.status')}
          placeholder={t('payments.anyStatus')}
          value={status}
          onChange={(e) => setStatus(e.target.value as PaymentStatus | '')}
          options={STATUS_VALUES.map((value) => ({ value, label: paymentStatusLabel(value) }))}
        />
        <DateRangeFields value={dates} onChange={setDates} />
        <div>
          <span className="mb-1.5 block text-sm font-medium text-text-primary">{t('payments.amountRange')}</span>
          <div className="flex items-center gap-2">
            <div className="min-w-0 flex-1">
              <TextField
                type="number"
                inputMode="numeric"
                aria-label={t('payments.minAmount')}
                placeholder={t('payments.min')}
                value={minAmount}
                min={0}
                onChange={(e) => setMinAmount(e.target.value)}
              />
            </div>
            <span className="shrink-0 text-sm text-text-secondary">{t('payments.to')}</span>
            <div className="min-w-0 flex-1">
              <TextField
                type="number"
                inputMode="numeric"
                aria-label={t('payments.maxAmount')}
                placeholder={t('payments.max')}
                value={maxAmount}
                min={0}
                onChange={(e) => setMaxAmount(e.target.value)}
              />
            </div>
          </div>
        </div>
      </ListFilterBar>

      {list.error && <p className="text-sm text-danger">{list.error}</p>}
      {list.loading && <SkeletonList rows={4} label={t('payments.loading')} />}

      {!list.loading && list.items.length === 0 && !list.error && (
        activeFilters > 0 ? (
          <ListEmptyState
            icon={<SearchX />}
            title={t('list.noResults')}
            message={t('payments.filteredEmpty')}
            action={{ label: t('list.clearFilters'), onClick: clearAll }}
          />
        ) : (
          <ListEmptyState
            illustrated
            icon={<ReceiptText />}
            title={t('payments.emptyTitle')}
            message={t('payments.emptyMessage')}
          />
        )
      )}

      <div className="space-y-3">
        {list.items.map((payment) => (
          <Card key={payment.id} className="flex items-center justify-between">
            <div className="min-w-0 flex-1">
              {/* Before capture the method is a placeholder, not a choice she made. */}
              <p className="truncate font-medium text-text-primary">
                {payment.status === 'CAPTURED' || payment.status === 'REFUNDED'
                  ? paymentMethodLabel(payment.method)
                  : t('payments.tripFare')}
              </p>
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
