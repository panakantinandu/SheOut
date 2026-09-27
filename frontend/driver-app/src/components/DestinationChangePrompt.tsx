import { CheckCircle2, MapPinned, XCircle } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Button, Card, CountdownRing, DestinationChangeCompare, IconCircle, Overlay, useTranslation } from '@sheout/design-system';
import { bookingApi } from '../api/client';
import type { BookingSummary, DestinationChange } from '../api/types';
import { apiErrorText } from '../lib/apiErrors';

/** Same pace as the Trip screen's booking poll. */
const POLL_MS = 4000;
/** The server's answer window, for drawing a full ring. The seconds themselves come from the server. */
const ANSWER_WINDOW_SECONDS = 120;

/**
 * Her rider has asked to go somewhere else, and it is her call.
 * <p>
 * A dialog over the trip rather than a card in it: she is riding, a card
 * below the map is easy to miss, and the question runs out. It shows exactly
 * what the rider was shown - the drop now and the drop asked for, the fare
 * and road distance each way - and a ring for the time left. Declining is a
 * plain, equal button: the trip then goes on as booked, and nothing counts
 * against her for saying no.
 * <p>
 * Once answered, a card on the trip says what was agreed; the map, the drop
 * card and the fare update from the booking itself.
 */
export function DestinationChangePrompt({ booking, onAnswered }: { booking: BookingSummary; onAnswered: () => void }) {
  const { t } = useTranslation();
  const [change, setChange] = useState<DestinationChange | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const deadline = useRef(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inProgress = booking.status === 'IN_PROGRESS';

  useEffect(() => {
    if (!inProgress) return;
    let cancelled = false;
    const load = () =>
      bookingApi
        .getDestinationChange(booking.id)
        .then((state) => {
          if (cancelled) return;
          setChange(state.change);
          if (state.change?.status === 'PENDING') {
            deadline.current = Date.now() + state.change.secondsLeft * 1000;
            setSecondsLeft(state.change.secondsLeft);
          }
        })
        .catch(() => undefined);
    load();
    const timer = window.setInterval(load, POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [booking.id, inProgress]);

  // A smooth count between polls, from the server's own figure.
  const pending = change?.status === 'PENDING';
  useEffect(() => {
    if (!pending) return;
    const tick = window.setInterval(() => {
      setSecondsLeft(Math.max(0, Math.round((deadline.current - Date.now()) / 1000)));
    }, 1000);
    return () => window.clearInterval(tick);
  }, [pending]);

  async function answer(accept: boolean) {
    setBusy(true);
    setError(null);
    try {
      setChange(await bookingApi.answerDestinationChange(booking.id, accept));
      onAnswered();
    } catch (err) {
      setError(apiErrorText(err, 'trip.destinationChange.answerError'));
      // Whatever it is now - most likely it ran out - is what she should see.
      bookingApi.getDestinationChange(booking.id).then((s) => setChange(s.change)).catch(() => undefined);
    } finally {
      setBusy(false);
    }
  }

  if (!inProgress || !change) return null;
  const open = pending && secondsLeft > 0;

  return (
    <>
      {change.status === 'ACCEPTED' && (
        <Card tone="success" className="flex items-start gap-3" data-testid="destination-change-accepted">
          <IconCircle tone="soft" color="green" icon={<CheckCircle2 />} />
          <div className="min-w-0 flex-1">
            <p className="font-heading text-card-title text-text-primary">{t('trip.destinationChange.acceptedTitle')}</p>
            <p className="mt-1 text-sm text-text-secondary">
              {t('trip.destinationChange.acceptedBody', { drop: change.newDrop.label, fare: change.newFare.toFixed(2), was: change.oldFare.toFixed(2) })}
            </p>
          </div>
        </Card>
      )}
      {change.status === 'DECLINED' && (
        <Card className="flex items-start gap-3" data-testid="destination-change-declined">
          <IconCircle tone="soft" icon={<XCircle />} />
          <div className="min-w-0 flex-1">
            <p className="font-heading text-card-title text-text-primary">{t('trip.destinationChange.declinedTitle')}</p>
            <p className="mt-1 text-sm text-text-secondary">
              {t('trip.destinationChange.declinedBody', { drop: change.oldDrop.label, fare: change.oldFare.toFixed(2) })}
            </p>
          </div>
        </Card>
      )}

      <Overlay open={open} label={t('trip.destinationChange.title')} className="px-4">
        <div className="max-h-[92vh] w-full max-w-sm overflow-y-auto rounded-card bg-surface p-5 shadow-overlay motion-safe:animate-pop-in" data-testid="destination-change-prompt">
          <div className="flex items-start gap-3">
            <IconCircle tone="soft" icon={<MapPinned />} />
            <div className="min-w-0 flex-1">
              <p className="font-heading text-section text-text-primary">{t('trip.destinationChange.title')}</p>
              <p className="mt-1 text-sm text-text-secondary">{t('trip.destinationChange.body')}</p>
            </div>
            <CountdownRing secondsLeft={secondsLeft} totalSeconds={ANSWER_WINDOW_SECONDS} />
          </div>
          <DestinationChangeCompare
            className="mt-4"
            audience="partner"
            oldDrop={change.oldDrop.label}
            newDrop={change.newDrop.label}
            oldFare={change.oldFare}
            newFare={change.newFare}
            oldDistanceKm={change.oldDistanceKm}
            newDistanceKm={change.newDistanceKm}
          />
          <p className="mt-3 text-sm text-text-secondary">{t('trip.destinationChange.declineIsFine', { drop: change.oldDrop.label })}</p>
          {error && <p className="mt-3 text-sm text-danger">{error}</p>}
          <div className="mt-5 flex gap-3">
            <Button variant="secondary" fullWidth disabled={busy} onClick={() => answer(false)} data-testid="decline-destination-change">
              {t('trip.destinationChange.decline')}
            </Button>
            <Button fullWidth disabled={busy} onClick={() => answer(true)} data-testid="accept-destination-change">
              {t('trip.destinationChange.accept')}
            </Button>
          </div>
        </div>
      </Overlay>
    </>
  );
}
