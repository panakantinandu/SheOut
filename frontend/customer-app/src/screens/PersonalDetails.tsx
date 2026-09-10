import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, TextField, TopHeader } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';

/**
 * REAL: reads GET /users/customer/me and saves through PUT
 * /users/customer/me - the endpoint already existed; only a screen was
 * missing, which is why this was a placeholder.
 * <p>
 * Phone number is display-only: it is the account identity in auth (one
 * phone, one account, one role) and this endpoint cannot change it, so
 * offering an editable field would imply a capability that does not exist.
 * Home and work addresses live on the same endpoint but get their own
 * screen - see SavedAddresses.
 */
export function PersonalDetails() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then((p) => {
        setProfile(p);
        setName(p.name ?? '');
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load profile'));
  }, []);

  async function handleSave() {
    if (!name.trim()) {
      setError('Name cannot be empty');
      return;
    }
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      // Addresses are passed back unchanged - PUT replaces the whole profile,
      // so omitting them here would silently wipe what SavedAddresses set.
      const updated = await usersApi.updateMyProfile({
        name: name.trim(),
        homeAddress: profile?.homeAddress ?? undefined,
        workAddress: profile?.workAddress ?? undefined,
      });
      setProfile(updated);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save changes');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Personal Details" onBack={() => navigate('/profile')} />
      {error && <p className="text-sm text-danger">{error}</p>}
      {saved && <p className="text-sm text-success">Saved.</p>}

      {!profile ? (
        <p className="text-center text-sm text-text-secondary">Loading...</p>
      ) : (
        <Card className="space-y-4">
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Your name" />
          <div>
            <p className="text-sm text-text-secondary">Mobile Number</p>
            <p className="text-text-primary">{profile.phoneNumber}</p>
            <p className="mt-1 text-xs text-text-secondary">
              Your number identifies your account and cannot be changed here.
            </p>
          </div>
          <Button fullWidth onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : 'Save Changes'}
          </Button>
        </Card>
      )}
    </div>
  );
}
