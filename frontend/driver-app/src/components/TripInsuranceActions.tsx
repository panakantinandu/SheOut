import { useEffect, useState } from 'react';
import { AccidentReportSheet, InsuredTripChip, TripCoverSheet, useTranslation } from '@sheout/design-system';
import { insuranceApi } from '../api/client';
import type { BookingStatus, TripCover } from '../api/types';

/**
 * On a trip that started: "Insured trip" when it has cover (and only then),
 * and "Report an accident" either way - an accident is reported whether or
 * not there is a policy to claim on.
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
        {t('trip.reportAccident')}
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
