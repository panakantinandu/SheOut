import { Bike, Car, HelpCircle, LogOut, ShieldCheck, Truck, User } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, ListRow, StatusBadge, TextField, TopHeader } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { DriverProfileSummary, VehicleType } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const VEHICLE_OPTIONS: { key: VehicleType; label: string; icon: JSX.Element }[] = [
  { key: 'BIKE', label: 'Bike', icon: <Bike className="h-4 w-4" /> },
  { key: 'AUTO', label: 'Auto', icon: <Truck className="h-4 w-4" /> },
  { key: 'CAB', label: 'Cab', icon: <Car className="h-4 w-4" /> },
];

/**
 * REAL: profile fetched from GET /api/v1/users/driver/me, edits saved via
 * PUT (same endpoint). Log Out clears the real session. Help & Support and
 * the notification bell are real screens now - the placeholders they used
 * to show are gone.
 */
export function Profile() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState('');
  const [vehicleType, setVehicleType] = useState<VehicleType>('BIKE');
  const [vehicleReg, setVehicleReg] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then((p) => {
        setProfile(p);
        setName(p.name ?? '');
        setVehicleType(p.vehicleType ?? 'BIKE');
        setVehicleReg(p.vehicleRegistrationNumber ?? '');
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load profile'));
  }, []);

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const updated = await usersApi.updateMyProfile({ name, vehicleType, vehicleRegistrationNumber: vehicleReg });
      setProfile(updated);
      setEditing(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save profile');
    } finally {
      setSaving(false);
    }
  }

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="My Profile" onBack={() => navigate('/home')} />

      <Card className="flex items-center gap-3">
        <IconCircle size="lg" tone="soft" icon={<User />} />
        <div className="min-w-0 flex-1">
          <p className="font-heading font-semibold text-text-primary">
            {profile ? profile.name || 'Add your name' : error || 'Loading...'}
          </p>
          <p className="text-sm text-text-secondary">{profile?.phoneNumber ?? ''}</p>
          {profile && (
            <StatusBadge tone={profile.verified ? 'success' : 'warning'} className="mt-1">
              {profile.verified ? 'Verified' : 'Unverified'}
            </StatusBadge>
          )}
        </div>
      </Card>

      {error && <p className="text-sm text-danger">{error}</p>}

      {profile && !editing && (
        <Card className="space-y-3">
          <div className="flex items-center justify-between text-sm">
            <span className="text-text-secondary">Vehicle</span>
            <span className="font-medium text-text-primary">{profile.vehicleType ?? 'Not set'}</span>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-text-secondary">Registration No.</span>
            <span className="font-medium text-text-primary">{profile.vehicleRegistrationNumber ?? 'Not set'}</span>
          </div>
          <Button variant="secondary" fullWidth onClick={() => setEditing(true)}>
            Edit Details
          </Button>
        </Card>
      )}

      {profile && editing && (
        <Card className="space-y-4">
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />

          <div>
            <span className="mb-1.5 block text-sm font-medium text-text-primary">Vehicle Type</span>
            <div className="flex gap-2">
              {VEHICLE_OPTIONS.map((opt) => (
                <button
                  key={opt.key}
                  type="button"
                  onClick={() => setVehicleType(opt.key)}
                  className={
                    vehicleType === opt.key
                      ? 'flex flex-1 items-center justify-center gap-1.5 rounded-full bg-primary py-2 text-sm font-semibold text-text-inverse'
                      : 'flex flex-1 items-center justify-center gap-1.5 rounded-full border border-border py-2 text-sm font-medium text-text-secondary'
                  }
                >
                  {opt.icon} {opt.label}
                </button>
              ))}
            </div>
          </div>

          <TextField label="Registration Number" value={vehicleReg} onChange={(e) => setVehicleReg(e.target.value)} />

          <div className="flex gap-3">
            <Button variant="secondary" fullWidth disabled={saving} onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button fullWidth disabled={saving} onClick={handleSave}>
              {saving ? 'Saving...' : 'Save'}
            </Button>
          </div>
        </Card>
      )}

      <Card className="divide-y divide-border p-0">
        <div className="p-4">
          <ListRow
            icon={<IconCircle tone="soft" size="sm" icon={<ShieldCheck />} />}
            label="Verification"
            onClick={() => navigate('/verification')}
          />
        </div>
        <div className="p-4">
          <ListRow
            icon={<IconCircle tone="soft" size="sm" icon={<HelpCircle />} />}
            label="Help & Support"
            onClick={() => navigate('/help')}
          />
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
