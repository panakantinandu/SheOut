import { useEffect, useState } from 'react';
import { AccidentReportSheet, InsuredTripChip, TripCoverSheet, useTranslation } from '@sheout/design-system';
import { insuranceApi } from '../api/client';
import type { BookingStatus, TripCover } from '../api/types';

/**
 * On her trip once it has started: the "Insured trip · ₹5,00,000 cover" chip
 * when - and only when - the server has a coverage row for it against a
 * policy that was in force, and "Report an accident or make a claim".
 * <p>
 * Nothing here is shown before the trip starts (there is no cover yet), and
 * nothing about insurance at all for a trip with no cover: the chip is a
 * claim about her safety and is only ever made from a record.
 */
export function TripInsuranceActions({ bookingId, status }: { bookingId: string; status: BookingStatus }) {
  const { t } = useTranslation();
  const [cover, setCover] = useState<TripCover | null>(null);
  const [showingCover, setShowingCover] = useState(false);
  const [reporting, setReporting] = useState(false);
  const started = status === 'IN_PROGRESS' || status === 'COMPLETED';

  useEffect(() => {
    if (!started) return;
    insuranceApi.tripCover(bookingId).then(setCover).catch(() => setCover(null));
  }, [bookingId, started]);

  if (!started) return null;
  return (
    <div className="flex flex-wrap items-center justify-between gap-2" data-testid="trip-insurance-actions">
      {cover ? <InsuredTripChip cover={cover} onOpen={() => setShowingCover(true)} /> : <span />}
      <button
        type="button"
        className="text-xs font-semibold text-danger underline-offset-2 hover:underline"
        onClick={() => setReporting(true)}
        data-testid="report-accident"
      >
        {t('tracking.reportAccident')}
      </button>
      <TripCoverSheet
        open={showingCover}
        cover={cover}
        onClose={() => setShowingCover(false)}
        onReportAccident={() => {
          setShowingCover(false);
          setReporting(true);
        }}
      />
      <AccidentReportSheet
        open={reporting}
        onClose={() => setReporting(false)}
        submit={(description) => insuranceApi.reportAccident(bookingId, description)}
      />
    </div>
  );
}
