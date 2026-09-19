import { AlertCircle, ChevronRight } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle } from '@sheout/design-system';
import { bookingApi } from '../api/client';
import type { PaymentHold } from '../api/types';

/**
 * Her last trip ended and is still unpaid, so she cannot book another - said
 * where she would try to, with the way to fix it one tap away.
 * <p>
 * Shown before she fills in a booking, not only after the server refuses
 * one: finding out at the last step, with a pickup and drop already chosen,
 * is the frustrating version of the same rule.
 */
export function UnpaidTripBanner({ refreshKey = 0 }: { refreshKey?: number }) {
  const navigate = useNavigate();
  const [hold, setHold] = useState<PaymentHold | null>(null);

  useEffect(() => {
    let cancelled = false;
    bookingApi
      .getPaymentHold()
      .then((h) => {
        if (!cancelled) setHold(h);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  if (!hold) return null;

  return (
    <Card
      tone="warning"
      className="flex items-center gap-3"
      onClick={() => navigate(`/tracking/${hold.bookingId}`)}
      data-testid="unpaid-trip-banner"
    >
      <IconCircle tone="soft" color="orange" icon={<AlertCircle />} />
      <div className="min-w-0 flex-1">
        <p className="font-heading font-semibold text-text-primary">Pay ₹{hold.amount.toFixed(0)} for your last trip</p>
        <p className="text-xs text-text-secondary">You can book your next ride once it is paid.</p>
      </div>
      <ChevronRight className="h-5 w-5 shrink-0 text-text-secondary" />
    </Card>
  );
}
