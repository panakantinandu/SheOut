import { useCallback, useState } from 'react';
import { ChevronRight, LifeBuoy, SearchX } from 'lucide-react';
import { Card } from './Card';
import { DateRangeFields, endOfDayIso, startOfDayIso, type DateRangeValue } from './DateRangeFields';
import { ListEmptyState } from './ListEmptyState';
import { ListFilterBar } from './ListFilterBar';
import { LoadMore } from './LoadMore';
import { SelectField } from './SelectField';
import { StatusBadge } from './StatusBadge';
import { usePagedList, type PagedResult } from '../lib/usePagedList';
import {
  SUPPORT_STATUS_OPTIONS,
  supportCategoryLabel,
  supportCategoryOptions,
  supportStatusLabel,
  supportStatusTone,
} from '../lib/support';

export interface SupportTicketListItem {
  id: string;
  category: string;
  subject: string;
  status: string;
  lastActivityAt: string;
}

export interface SupportTicketFilters {
  page: number;
  status?: string[];
  category?: string[];
  from?: string;
  to?: string;
}

export interface SupportTicketListProps {
  audience: 'customer' | 'driver';
  fetchPage: (filters: SupportTicketFilters) => Promise<PagedResult<SupportTicketListItem>>;
  onOpen: (ticketId: string) => void;
}

/**
 * "My tickets": status, category and last update, newest activity first.
 * <p>
 * Built on the same pieces as the trip and payment history lists -
 * usePagedList, ListFilterBar, DateRangeFields, LoadMore and the two kinds
 * of empty state - so filtering tickets works exactly the way filtering
 * trips already does.
 */
export function SupportTicketList({ audience, fetchPage, onOpen }: SupportTicketListProps) {
  const [status, setStatus] = useState('');
  const [category, setCategory] = useState('');
  const [dates, setDates] = useState<DateRangeValue>({ from: '', to: '' });

  const load = useCallback(
    (page: number) =>
      fetchPage({
        page,
        status: status ? [status] : undefined,
        category: category ? [category] : undefined,
        from: startOfDayIso(dates.from),
        to: endOfDayIso(dates.to),
      }),
    [fetchPage, status, category, dates.from, dates.to]
  );
  const list = usePagedList(load, [status, category, dates.from, dates.to]);
  const activeCount = [status, category, dates.from, dates.to].filter(Boolean).length;

  return (
    <div className="space-y-3">
      <ListFilterBar
        activeCount={activeCount}
        onClearAll={() => {
          setStatus('');
          setCategory('');
          setDates({ from: '', to: '' });
        }}
      >
        <SelectField
          label="Status"
          placeholder="Any status"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          options={SUPPORT_STATUS_OPTIONS}
        />
        <SelectField
          label="Category"
          placeholder="Any category"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          options={supportCategoryOptions(audience)}
        />
        <DateRangeFields value={dates} onChange={setDates} label="Raised between" />
      </ListFilterBar>

      {list.error && <p className="text-sm text-danger">{list.error}</p>}
      {list.loading && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {!list.loading && !list.error && list.items.length === 0 && (
        activeCount > 0 ? (
          <ListEmptyState
            icon={<SearchX />}
            title="No tickets match your filters"
            message="Your tickets are still here. Clear the filters to see them all."
            action={{
              label: 'Clear filters',
              onClick: () => {
                setStatus('');
                setCategory('');
                setDates({ from: '', to: '' });
              },
            }}
          />
        ) : (
          <ListEmptyState
            icon={<LifeBuoy />}
            title="No tickets yet"
            message="When you raise an issue, it appears here with every reply from support."
          />
        )
      )}

      {list.items.map((ticket) => (
        <Card key={ticket.id} className="flex items-center gap-3" onClick={() => onOpen(ticket.id)}>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-text-primary">{ticket.subject}</p>
            <p className="truncate text-xs text-text-secondary">
              {supportCategoryLabel(ticket.category, audience)} &middot; updated{' '}
              {new Date(ticket.lastActivityAt).toLocaleString([], {
                day: 'numeric',
                month: 'short',
                hour: '2-digit',
                minute: '2-digit',
              })}
            </p>
            <StatusBadge tone={supportStatusTone(ticket.status)} className="mt-1">
              {supportStatusLabel(ticket.status)}
            </StatusBadge>
          </div>
          <ChevronRight className="h-5 w-5 shrink-0 text-text-secondary" aria-hidden="true" />
        </Card>
      ))}

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
