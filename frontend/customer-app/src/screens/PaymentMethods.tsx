import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Card, StatusBadge, TopHeader, paymentMethodLabel, paymentStatusLabel } from '@sheout/design-system';
import { ApiError, bookingApi, paymentsApi } from '../api/client';
import type { BookingSummary, PaymentSummary } from '../api/types';

const TONES = {
  CAPTURED: 'success',
  PENDING: 'warning',
  FAILED: 'danger',
  REFUNDED: 'neutral',
} as const;

/**
 * REAL: every payment against this customer's own bookings, read from
 * GET /payments/bookings/{id} for each booking from GET /bookings/me.
 * <p>
 * This is deliberately payment HISTORY, not saved cards or UPI IDs. The
 * payments module stores a payment per booking and nothing else - there is
 * no stored-instrument concept anywhere in the backend - so a "add a card"
 * UI would be inventing a capability. The old placeholder here claimed
 * there was no payments module at all, which stopped being true once one
 * was built.
 * <p>
 * One request per booking is an N+1, accepted because PaymentApi is
 * per-booking by design and a customer's own booking list is small.
 */
export function PaymentMethods() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<{ booking: BookingSummary; payment: PaymentSummary | null }[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    bookingApi
      .listMine()
      .then(async (bookings) => {
        const resolved = await Promise.all(
          bookings.map(async (booking) => ({
            booking,
            // A booking with no payment row yet 404s - normal until a trip
            // completes, so it becomes null rather than an error.
            payment: await paymentsApi.getForBooking(booking.id).catch(() => null),
          }))
        );
        setRows(resolved.filter((r) => r.payment !== null));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load payments'));
  }, []);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Payment History" onBack={() => navigate('/profile')} />
      {error && <p className="text-sm text-danger">{error}</p>}

      <Card>
        <p className="text-sm text-text-primary">Payments are taken per trip via UPI or cash.</p>
        <p className="mt-1 text-xs text-text-secondary">
          No card or UPI ID is stored on your account - there is nothing saved to manage here, so this shows what you
          have actually been charged.
        </p>
      </Card>

      {!rows ? (
        <p className="text-center text-sm text-text-secondary">Loading...</p>
      ) : rows.length === 0 ? (
        <Card className="text-center">
          <p className="font-heading font-semibold text-text-primary">No payments yet</p>
          <p className="mt-1 text-sm text-text-secondary">Payments appear here once a trip is completed.</p>
        </Card>
      ) : (
        <div className="space-y-3">
          {rows.map(({ booking, payment }) => (
            <Card key={booking.id} className="flex items-center justify-between">
              <div>
                <p className="font-medium text-text-primary">
                  {booking.type === 'RIDE' ? 'Ride' : 'Delivery'} &middot; {paymentMethodLabel(payment!.method)}
                </p>
                <p className="text-xs text-text-secondary">{new Date(payment!.createdAt).toLocaleString()}</p>
              </div>
              <div className="flex items-center gap-3">
                <AmountText amount={payment!.amount} />
                <StatusBadge tone={TONES[payment!.status]}>{paymentStatusLabel(payment!.status)}</StatusBadge>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
