import { Briefcase, House } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, TextField, TopHeader } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';

/**
 * REAL: home/work addresses are columns on the customer profile, read and
 * written through the same GET/PUT /users/customer/me this app already
 * used. No screen existed for them, which is why this was a placeholder.
 * <p>
 * Exactly two addresses, not an arbitrary list: the backend stores
 * homeAddress and workAddress and nothing else. An "Add address" button
 * would imply storage that does not exist. They are free text, since
 * nothing geocodes them into coordinates yet.
 */
export function SavedAddresses() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [home, setHome] = useState('');
  const [work, setWork] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then((p) => {
        setProfile(p);
        setHome(p.homeAddress ?? '');
        setWork(p.workAddress ?? '');
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load addresses'));
  }, []);

  async function handleSave() {
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      // name is sent back unchanged - PUT replaces the profile, so leaving it
      // out would wipe the name PersonalDetails set.
      const updated = await usersApi.updateMyProfile({
        name: profile?.name ?? '',
        homeAddress: home.trim() || undefined,
        workAddress: work.trim() || undefined,
      });
      setProfile(updated);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save addresses');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Saved Addresses" onBack={() => navigate('/profile')} />
      {error && <p className="text-sm text-danger">{error}</p>}
      {saved && <p className="text-sm text-success">Saved.</p>}

      {!profile ? (
        <p className="text-center text-sm text-text-secondary">Loading...</p>
      ) : (
        <Card className="space-y-4">
          <div className="flex items-center gap-2">
            <IconCircle size="sm" tone="soft" icon={<House />} />
            <span className="font-medium text-text-primary">Home</span>
          </div>
          <TextField value={home} onChange={(e) => setHome(e.target.value)} placeholder="Add a home address" />

          <div className="flex items-center gap-2 pt-2">
            <IconCircle size="sm" tone="soft" icon={<Briefcase />} />
            <span className="font-medium text-text-primary">Work</span>
          </div>
          <TextField value={work} onChange={(e) => setWork(e.target.value)} placeholder="Add a work address" />

          <Button fullWidth onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : 'Save Addresses'}
          </Button>
          <p className="text-xs text-text-secondary">
            Home and Work are the two addresses your account stores. They are labels for now - booking still asks you to
            pick pickup and drop points.
          </p>
        </Card>
      )}
    </div>
  );
}
