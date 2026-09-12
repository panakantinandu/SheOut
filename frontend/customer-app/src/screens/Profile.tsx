import { BadgeCheck, FileText, HelpCircle, Info, Lock, LogOut, MapPin, Receipt, User } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, ConfirmDialog, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';

/**
 * REAL: name/phone fetched from GET /api/v1/users/customer/me. Log Out
 * clears the real session. The menu items below (Personal Details, Saved
 * Addresses, Payment History, Help & Support, About SheOut) have no
 * sub-screens - none were asked for in this pass - so they show a mock
 * "not implemented yet" instead of silently doing nothing. (Previously
 * these had a chevron implying they were tappable but no onClick at all -
 * that was the bug, not that they're unbuilt.)
 */
export function Profile() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [confirmingLogout, setConfirmingLogout] = useState(false);
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load profile'));
  }, []);

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="My Profile" onBack={() => navigate('/home')} />

      <Card className="flex items-center gap-3">
        <IconCircle size="lg" tone="soft" icon={<User />} />
        <div>
          <p className="font-heading font-semibold text-text-primary">{profile?.name || 'Add your name'}</p>
          <p className="text-sm text-text-secondary">
            {profile ? profile.phoneNumber || 'Signed in with Google' : error || 'Loading...'}
          </p>
        </div>
      </Card>

      {/* Every row used to sit in one undifferentiated card, and Identity
          Verification and Payments shared a single cell with no rule
          between them - so two unrelated things read as one control, and
          nothing on the screen said what any group was for. Grouped into
          named sections instead, the same heading-over-card shape Home and
          Wallet already use. */}
      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Account</h2>
        <Card className="divide-y divide-border p-0">
          <div className="p-4">
            <ListRow icon={<IconCircle tone="soft" size="sm" icon={<User />} />} label="Personal Details" onClick={() => navigate('/profile/details')} />
          </div>
          <div className="p-4">
            <ListRow icon={<IconCircle tone="soft" size="sm" icon={<MapPin />} />} label="Saved Addresses" onClick={() => navigate('/profile/addresses')} />
          </div>
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Verification</h2>
        <Card className="p-0">
          <div className="p-4">
            <ListRow icon={<IconCircle tone="soft" size="sm" icon={<BadgeCheck />} />} label="Identity Verification" onClick={() => navigate('/verification')} />
          </div>
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Payments</h2>
        <Card className="p-0">
          <div className="p-4">
            <ListRow icon={<IconCircle tone="soft" size="sm" icon={<Receipt />} />} label="Payment History" onClick={() => navigate('/profile/payments')} />
          </div>
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">Support</h2>
        <Card className="divide-y divide-border p-0">
          <div className="p-4">
            <ListRow icon={<IconCircle tone="soft" size="sm" icon={<HelpCircle />} />} label="Help & Support" onClick={() => navigate('/help')} />
          </div>
          <div className="p-4">
            <ListRow icon={<IconCircle tone="soft" size="sm" icon={<Info />} />} label="About SheOut" onClick={() => navigate('/about')} />
          </div>
          <div className="p-4">
            <ListRow icon={<IconCircle tone="soft" size="sm" icon={<Lock />} />} label="Privacy Policy" onClick={() => navigate('/privacy')} />
          </div>
          <div className="p-4">
            <ListRow icon={<IconCircle tone="soft" size="sm" icon={<FileText />} />} label="Terms of Service" onClick={() => navigate('/terms')} />
          </div>
        </Card>
      </section>

      <Card className="p-0">
        <div className="p-4">
          <ListRow
            icon={<IconCircle color="red" tone="soft" size="sm" icon={<LogOut />} />}
            label="Log Out"
            chevron={false}
            onClick={() => setConfirmingLogout(true)}
          />
        </div>
      </Card>

      {/* Logging out used to fire on a single tap of the row above, with
          no way back. */}
      <ConfirmDialog
        open={confirmingLogout}
        title="Log out?"
        message="You will need your mobile number and an OTP to sign back in."
        confirmLabel="Log Out"
        destructive
        onConfirm={handleLogout}
        onCancel={() => setConfirmingLogout(false)}
      />
    </div>
  );
}
