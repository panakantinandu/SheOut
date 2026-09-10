import { Bell, CheckCircle2, XCircle } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, StatusBadge, TopHeader } from '@sheout/design-system';
import { ApiError, notificationsApi } from '../api/client';
import type { NotificationView } from '../api/types';

const LABELS: Record<NotificationView['type'], string> = {
  BOOKING_REQUESTED: 'New booking nearby',
  BOOKING_ACCEPTED: 'You accepted a booking',
  BOOKING_COMPLETED: 'Trip completed',
  BOOKING_CANCELLED: 'Booking cancelled',
  ACCOUNT_VERIFIED: 'Your account was verified',
  SOS_ALERT: 'SOS alert',
};

/**
 * REAL: GET /notifications/me - the caller's own delivery history, the
 * same rows the notifications module writes when it sends an alert.
 * Replaces the bell's placeholder, which claimed no notifications module
 * existed; one has since been built.
 */
export function Notifications() {
  const navigate = useNavigate();
  const [items, setItems] = useState<NotificationView[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    notificationsApi
      .listMine()
      .then(setItems)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load notifications'));
  }, []);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Notifications" onBack={() => navigate('/home')} />
      {error && <p className="text-sm text-danger">{error}</p>}

      {!items ? (
        <p className="text-center text-sm text-text-secondary">Loading...</p>
      ) : items.length === 0 ? (
        <Card className="text-center">
          <IconCircle size="lg" tone="soft" icon={<Bell />} className="mx-auto" />
          <p className="mt-3 font-heading font-semibold text-text-primary">Nothing yet</p>
          <p className="mt-1 text-sm text-text-secondary">Alerts about your trips appear here once they are sent.</p>
        </Card>
      ) : (
        <div className="space-y-3">
          {items.map((n) => (
            <Card key={n.id} className="flex items-start gap-3">
              <IconCircle size="sm" tone="soft" icon={n.status === 'SENT' ? <CheckCircle2 /> : <XCircle />} />
              <div className="flex-1">
                <p className="font-medium text-text-primary">{LABELS[n.type] ?? n.type}</p>
                <p className="text-xs text-text-secondary">
                  {new Date(n.createdAt).toLocaleString()} &middot; {n.channel}
                </p>
                {n.status === 'FAILED' && (
                  <p className="mt-1 text-xs text-danger">Delivery failed{n.failureReason ? `: ${n.failureReason}` : ''}</p>
                )}
              </div>
              <StatusBadge tone={n.status === 'SENT' ? 'success' : 'danger'}>{n.status}</StatusBadge>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
