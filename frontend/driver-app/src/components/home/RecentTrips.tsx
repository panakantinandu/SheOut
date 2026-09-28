import { ArrowRight, Bike } from 'lucide-react';
import {
  AmountText,
  Card,
  ListEmptyState,
  ServiceArt,
  StatusBadge,
  bookingCategoryLabel,
  isAwaitingPayment,
  serviceArtFor,
  tripStatusLabel,
  useTranslation,
  type StatusTone,
} from '@sheout/design-system';
import type { BookingSummary } from '../../api/types';

function tone(b: BookingSummary): StatusTone {
  if (isAwaitingPayment(b)) return 'warning';
  if (b.status === 'COMPLETED') return 'success';
  if (b.status === 'CANCELLED') return 'danger';
  return 'primary';
}

/**
 * Her last few trips, in the same row the rider app's My Bookings uses:
 * service tile, what it was, where from and to, when, the fare in bold and
 * its status. "See all" is the Bookings tab.
 * <p>
 * With none yet, the brand illustration and one line saying how to get the
 * first - not an empty box.
 */
export function RecentTrips({ trips, onSeeAll, onOpen }: { trips: BookingSummary[]; onSeeAll: () => void; onOpen: (id: string) => void }) {
  const { t } = useTranslation();
  return (
    <section data-testid="recent-trips">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="font-heading text-section text-text-primary">{t('home.recentTrips')}</h2>
        {trips.length > 0 && (
          <button type="button" onClick={onSeeAll} className="flex min-h-[44px] items-center gap-1 px-1 text-sm font-semibold text-primary" data-testid="see-all-trips">
            {t('home.seeAll')}
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </button>
        )}
      </div>
      {trips.length === 0 ? (
        <ListEmptyState illustrated icon={<Bike />} title={t('home.noTripsTitle')} message={t('home.noTripsBody')} />
      ) : (
        <div className="space-y-3">
          {trips.map((trip) => (
            <Card key={trip.id} className="flex items-start gap-3 overflow-hidden p-4" onClick={() => onOpen(trip.id)} data-testid="recent-trip">
              <ServiceArt kind={serviceArtFor(trip.category)} size="sm" className="mt-0.5 shrink-0" />
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <p className="min-w-0 break-words text-sm font-semibold text-text-primary">{bookingCategoryLabel(trip.category)}</p>
                  <AmountText amount={trip.finalFare ?? trip.fareEstimate} className="shrink-0 font-bold" />
                </div>
                <p className="mt-1 truncate text-xs text-text-secondary">
                  {trip.pickup.label} → {trip.drop.label}
                </p>
                <p className="mt-1 text-caption text-text-secondary">
                  {new Date(trip.completedAt ?? trip.cancelledAt ?? trip.requestedAt).toLocaleString([], { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })}
                </p>
                <StatusBadge tone={tone(trip)} className="mt-2">
                  {tripStatusLabel(trip)}
                </StatusBadge>
              </div>
            </Card>
          ))}
        </div>
      )}
    </section>
  );
}
