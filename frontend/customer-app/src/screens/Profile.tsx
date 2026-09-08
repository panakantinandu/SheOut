import { HelpCircle, Info, LogOut, MapPin, Shield, User } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, IconCircle, ListRow, TopHeader } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { mockAction } from '../lib/mockAction';

/**
 * REAL: name/phone fetched from GET /api/v1/users/customer/me. Log Out
 * clears the real session. The menu items below (Personal Details, Saved
 * Addresses, Payment Methods, Help & Support, About SheOut) have no
 * sub-screens - none were asked for in this pass - so they show a mock
 * "not implemented yet" instead of silently doing nothing. (Previously
 * these had a chevron implying they were tappable but no onClick at all -
 * that was the bug, not that they're unbuilt.)
 */
export function Profile() {
  const navigate = useNavigate();
  const { logout } = useAuth();
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

      <Card className="divide-y divide-border p-0">
        <div className="p-4">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<User />} />} label="Personal Details" onClick={() => mockAction('Personal Details', 'no edit-profile screen built yet')} />
        </div>
        <div className="p-4">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<MapPin />} />} label="Saved Addresses" onClick={() => mockAction('Saved Addresses', 'no saved-addresses screen built yet')} />
        </div>
        <div className="p-4">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<Shield />} />} label="Payment Methods" onClick={() => mockAction('Payment Methods', 'no payments module on the backend')} />
        </div>
        <div className="p-4">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<HelpCircle />} />} label="Help & Support" onClick={() => mockAction('Help & Support', 'no support screen built yet')} />
        </div>
        <div className="p-4">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<Info />} />} label="About SheOut" onClick={() => mockAction('About SheOut', 'no about screen built yet')} />
        </div>
      </Card>

      <Card className="p-0">
        <div className="p-4">
          <ListRow
            icon={<IconCircle color="red" tone="soft" size="sm" icon={<LogOut />} />}
            label="Log Out"
            chevron={false}
            onClick={handleLogout}
          />
        </div>
      </Card>
    </div>
  );
}
