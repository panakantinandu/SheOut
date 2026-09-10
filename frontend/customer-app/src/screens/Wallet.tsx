import { PlusCircle, Receipt, Send } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { AmountText, Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { mockAction } from '../lib/mockAction';

/**
 * FULLY MOCK - the payments module doesn't exist on the backend at all
 * (no wallet balance, no transaction history endpoints). Everything below
 * is hardcoded placeholder data so the screen matches the mockup's layout;
 * none of it reflects anything real.
 */
const MOCK_BALANCE = 1250;
const MOCK_TRANSACTIONS = [
  { id: '1', label: 'Ride Payment', date: '12 Apr 2026 · 10:24 AM', amount: 56, sign: 'negative' as const },
  { id: '2', label: 'Parcel Delivery', date: '11 Apr 2026 · 04:15 PM', amount: 120, sign: 'positive' as const },
  { id: '3', label: 'Lunch Box Order', date: '10 Apr 2026 · 12:36 PM', amount: 240, sign: 'negative' as const },
];

export function Wallet() {
  const navigate = useNavigate();

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Wallet" onBack={() => navigate('/home')} />

      <Card variant="primary">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm opacity-90">Available Balance (mock)</p>
            <AmountText amount={MOCK_BALANCE} size="lg" className="text-text-inverse" />
          </div>
          <button
            className="rounded-full bg-surface px-4 py-2 text-sm font-semibold text-primary"
            onClick={() => mockAction('Add Money', 'there is no wallet balance to top up - trips are paid per ride by UPI or cash')}
          >
            Add Money
          </button>
        </div>
      </Card>

      <div className="flex justify-around">
        <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<PlusCircle />} />} label="Add Money" onClick={() => mockAction('Add Money', 'there is no wallet balance to top up - trips are paid per ride by UPI or cash')} />
        <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<Send />} />} label="Send Money" onClick={() => mockAction('Send Money', 'no wallet-to-wallet transfers exist - payments are per trip only')} />
        <ListRow layout="stacked" icon={<IconCircle tone="soft" icon={<Receipt />} />} label="Transactions" onClick={() => navigate('/profile/payments')} />
      </div>

      <div>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Recent Transactions (mock)</h2>
        <Card className="divide-y divide-border p-0">
          {MOCK_TRANSACTIONS.map((tx) => (
            <div key={tx.id} className="flex items-center justify-between p-4">
              <div>
                <p className="text-sm font-medium text-text-primary">{tx.label}</p>
                <p className="text-xs text-text-secondary">{tx.date}</p>
              </div>
              <AmountText amount={tx.amount} sign={tx.sign} />
            </div>
          ))}
        </Card>
      </div>
    </div>
  );
}
