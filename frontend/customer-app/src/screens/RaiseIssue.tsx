import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, RaiseIssueForm, SafetyText, TopHeader, bookingCategoryLabel, showToast } from '@sheout/design-system';
import type { RaiseIssueValues, SelectOption } from '@sheout/design-system';
import { ApiError, bookingApi, supportApi } from '../api/client';
import { localEmergencyNumber } from '../lib/emergency';
import { useTranslation } from '@sheout/design-system';

/**
 * Raising a support ticket.
 * <p>
 * The trip picker is the rider's own recent trips, fetched from her own
 * history - the same list Bookings shows - so she links a trip by recognising
 * it, not by knowing an id. The server checks the link is really hers anyway.
 */
export function RaiseIssue() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [bookingOptions, setBookingOptions] = useState<SelectOption[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    bookingApi
      .search({ page: 0, pageSize: 20 })
      .then((page) => {
        if (cancelled) return;
        setBookingOptions(
          page.items.map((b) => ({
            value: b.id,
            label: `${bookingCategoryLabel(b.category)} · ${new Date(b.requestedAt).toLocaleDateString([], {
              day: 'numeric',
              month: 'short',
            })} · ${t('help.toPlace', { place: b.drop.label })}`,
          }))
        );
      })
      .catch(() => {
        // Linking a trip is optional. Without the list she can still raise
        // the issue and mention the trip in what she writes.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function submit(values: RaiseIssueValues) {
    setSubmitting(true);
    setError(null);
    try {
      const ticket = await supportApi.raiseTicket(values);
      showToast(t('help.sent'));
      navigate(`/help/tickets/${ticket.id}`, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('help.sendError'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('help.raiseIssue')} onBack={() => navigate('/help')} />
      <RaiseIssueForm
        audience="customer"
        bookingOptions={bookingOptions}
        submitting={submitting}
        error={error}
        onSubmit={submit}
        safetyNotice={
          <div className="space-y-2">
            <p className="font-semibold">
              <SafetyText k="raiseIssue.dangerTitle" />
            </p>
            <p className="text-text-secondary">
              <SafetyText k="raiseIssue.dangerBody" values={{ number: localEmergencyNumber().number }} />
            </p>
            <Button type="button" variant="danger" size="md" onClick={() => navigate('/sos')}>
              {t('help.openSos')}
            </Button>
          </div>
        }
      />
    </div>
  );
}
