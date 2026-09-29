import { CheckCircle2, Clock, MapPinned, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Button, Card, DestinationChangeCompare, IconCircle, Overlay, useTranslation } from '@sheout/design-system';
import { ApiError, bookingApi } from '../api/client';
import type { BookingSummary, DestinationChangeState, FareQuote, GeoAddress } from '../api/types';
import { apiErrorText } from '../lib/apiErrors';
import { useSavedPlaces } from '../lib/useSavedPlaces';
import { LocationPicker } from './LocationPicker';

/** Same pace as the booking poll on this screen. */
const POLL_MS = 3000;

interface Proposal {
  drop: GeoAddress;
  quote: FareQuote | null;
  /** Road distance of the trip as booked, for the before/after - from the same quote endpoint. */
  currentKm: number | null;
}

/**
 * "Change destination", during a trip.
 * <p>
 * She picks the new drop with the same picker she booked with, is shown the
 * trip as it is against the trip as it would be - drop, fare and distance,
 * both priced by the ordinary quote endpoint from her pickup - and only then
 * sends it. Her partner has to accept; until she does the trip goes on to the
 * old drop, and this card says so. Accepted, declined or unanswered, the
 * outcome stays on the card for the rest of the trip.
 * <p>
 * Once her partner has answered one request she cannot ask again on this
 * trip (the server's rule); the button then goes, and "End trip here" is the
 * way to get off early.
 */
export function ChangeDestination({ booking }: { booking: BookingSummary }) {
  const { t } = useTranslation();
  const savedPlaces = useSavedPlaces();
  const [state, setState] = useState<DestinationChangeState | null>(null);
  const [picking, setPicking] = useState(false);
  const [proposal, setProposal] = useState<Proposal | null>(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inProgress = booking.status === 'IN_PROGRESS';

  useEffect(() => {
    if (!inProgress) return;
    let cancelled = false;
    const load = () =>
      bookingApi
        .getDestinationChange(booking.id)
        .then((s) => {
          if (!cancelled) setState(s);
        })
        .catch(() => undefined);
    load();
    const timer = window.setInterval(load, POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [booking.id, inProgress]);

  async function priceIt(drop: GeoAddress) {
    setError(null);
    setProposal({ drop, quote: null, currentKm: null });
    const base = { type: booking.type, category: booking.category, pickup: booking.pickup };
    try {
      const [next, current] = await Promise.all([
        bookingApi.quote({ ...base, drop }),
        bookingApi.quote({ ...base, drop: booking.drop }).catch(() => null),
      ]);
      setProposal({ drop, quote: next, currentKm: current?.distanceKm ?? null });
    } catch (err) {
      setProposal(null);
      setError(apiErrorText(err, 'changeDestination.quoteError'));
    }
  }

  async function send() {
    if (!proposal?.quote) return;
    setSending(true);
    setError(null);
    try {
      const change = await bookingApi.requestDestinationChange(booking.id, proposal.drop, proposal.quote.fareEstimate);
      setState({ change, canRequest: false });
      setProposal(null);
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'DESTINATION_FARE_CHANGED') {
        // Priced again, shown again - nothing is sent at a fare she has not seen.
        setError(t('changeDestination.fareChanged'));
        await priceIt(proposal.drop);
      } else {
        setError(apiErrorText(err, 'changeDestination.sendError'));
      }
    } finally {
      setSending(false);
    }
  }

  const change = state?.change ?? null;
  if (!inProgress) return null;

  return (
    <>
      {change?.status === 'PENDING' && (
        <Card tone="brand" className="space-y-3" data-testid="destination-change-pending">
          <div className="flex items-start gap-3">
            <IconCircle tone="soft" icon={<Clock />} />
            <div className="min-w-0 flex-1">
              <p className="font-heading text-card-title text-text-primary">{t('changeDestination.waitingTitle')}</p>
              <p className="mt-1 text-sm text-text-secondary">
                {t('changeDestination.waitingBody', { seconds: change.secondsLeft, drop: change.oldDrop.label })}
              </p>
            </div>
          </div>
          <DestinationChangeCompare
            oldDrop={change.oldDrop.label}
            newDrop={change.newDrop.label}
            oldFare={change.oldFare}
            newFare={change.newFare}
            oldDistanceKm={change.oldDistanceKm}
            newDistanceKm={change.newDistanceKm}
          />
        </Card>
      )}

      {change?.status === 'ACCEPTED' && (
        <Card tone="success" className="flex items-start gap-3" data-testid="destination-change-accepted">
          <IconCircle tone="soft" color="green" icon={<CheckCircle2 />} />
          <div className="min-w-0 flex-1">
            <p className="font-heading text-card-title text-text-primary">{t('changeDestination.acceptedTitle')}</p>
            <p className="mt-1 text-sm text-text-secondary">
              {t('changeDestination.acceptedBody', { drop: change.newDrop.label, fare: change.newFare.toFixed(2), was: change.oldFare.toFixed(2) })}
            </p>
          </div>
        </Card>
      )}

      {(change?.status === 'DECLINED' || change?.status === 'EXPIRED') && (
        <Card tone="warning" className="flex items-start gap-3" data-testid={`destination-change-${change.status.toLowerCase()}`}>
          <IconCircle tone="soft" color="orange" icon={<XCircle />} />
          <div className="min-w-0 flex-1">
            <p className="font-heading text-card-title text-text-primary">
              {change.status === 'DECLINED' ? t('changeDestination.declinedTitle') : t('changeDestination.expiredTitle')}
            </p>
            <p className="mt-1 text-sm text-text-secondary">
              {t('changeDestination.continuesBody', { drop: change.oldDrop.label, fare: change.oldFare.toFixed(2) })}
            </p>
          </div>
        </Card>
      )}

      {state?.canRequest && (
        <Button
          variant="secondary"
          fullWidth
          icon={<MapPinned className="h-4 w-4" />}
          onClick={() => { setError(null); setPicking(true); }}
          data-testid="change-destination"
        >
          {change?.status === 'EXPIRED' ? t('changeDestination.askAgain') : t('changeDestination.button')}
        </Button>
      )}
      {error && !proposal && <p className="text-center text-sm text-danger">{error}</p>}

      <LocationPicker
        open={picking}
        title={t('changeDestination.pickTitle')}
        saved={savedPlaces}
        markerKind="drop"
        startAt={booking.drop}
        onSelect={(address) => void priceIt(address)}
        onClose={() => setPicking(false)}
      />

      <Overlay open={proposal !== null} label={t('changeDestination.confirmTitle')} align="sheet" onDismiss={() => !sending && setProposal(null)}>
        <div className="max-h-[92vh] w-full overflow-y-auto rounded-t-[1.75rem] bg-surface px-5 pb-8 pt-5 shadow-overlay motion-safe:animate-sheet-up" data-testid="destination-change-sheet">
          <p className="font-heading text-section text-text-primary">{t('changeDestination.confirmTitle')}</p>
          {proposal && !proposal.quote ? (
            <p className="mt-3 text-sm text-text-secondary">{t('changeDestination.pricing')}</p>
          ) : proposal?.quote ? (
            <>
              <DestinationChangeCompare
                className="mt-3"
                oldDrop={booking.drop.label}
                newDrop={proposal.drop.label}
                oldFare={booking.fareEstimate}
                newFare={proposal.quote.fareEstimate}
                oldDistanceKm={proposal.currentKm}
                newDistanceKm={proposal.quote.distanceKm}
              />
              {booking.promoDiscount > 0 && (
                <p className="mt-2 text-sm text-text-secondary">
                  {t('changeDestination.promoStill', {
                    discount: booking.promoDiscount.toFixed(2),
                    pay: Math.max(0, proposal.quote.fareEstimate - booking.promoDiscount).toFixed(2),
                  })}
                </p>
              )}
              <p className="mt-3 text-sm text-text-secondary">{t('changeDestination.partnerDecides', { drop: booking.drop.label })}</p>
            </>
          ) : null}
          {error && <p className="mt-3 text-sm text-danger">{error}</p>}
          <div className="mt-5 flex gap-3">
            <Button variant="secondary" fullWidth disabled={sending} onClick={() => setProposal(null)}>
              {t('common.cancel')}
            </Button>
            <Button fullWidth disabled={sending || !proposal?.quote} onClick={send} data-testid="send-destination-change">
              {sending ? t('changeDestination.sending') : t('changeDestination.send')}
            </Button>
          </div>
        </div>
      </Overlay>
    </>
  );
}
