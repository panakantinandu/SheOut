import { Briefcase, Home, MapPin, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, SkeletonCard, TopHeader, useTranslation } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary, GeoAddress, SavedPlace } from '../api/types';
import { LocationPicker } from '../components/LocationPicker';
import { apiErrorText } from '../lib/apiErrors';

type Slot = 'home' | 'work';

/**
 * Home and Work, as places on the map rather than sentences.
 * <p>
 * They used to be two free-text boxes. What she typed was only ever readable
 * by her: a booking needs a point, so "Flat 4, Kavuri Hills" could not be a
 * pickup and she searched for the same place every time. Each is now set with
 * the same picker the booking screens use - search, or drop a pin - and once
 * set it is one tap on either of those screens.
 * <p>
 * An address saved as text before this still shows, with a line saying it
 * needs placing on the map. Nothing she typed is thrown away, and nothing
 * pretends to be usable when it is not.
 */
export function SavedAddresses() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [picking, setPicking] = useState<Slot | null>(null);

  const load = useCallback(
    () =>
      usersApi
        .getMyProfile()
        .then(setProfile)
        .catch((err) => setError(apiErrorText(err, 'addresses.loadError'))),
    []
  );

  useEffect(() => {
    void load();
  }, [load]);

  async function save(slot: Slot, place: SavedPlace | null) {
    if (!profile) return;
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      const updated = await usersApi.updateMyProfile({
        name: profile.name ?? '',
        dateOfBirth: profile.dateOfBirth ?? '',
        email: profile.email ?? undefined,
        home: slot === 'home' ? place : profile.home,
        work: slot === 'work' ? place : profile.work,
      });
      setProfile(updated);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('addresses.saveError'));
    } finally {
      setSaving(false);
    }
  }

  const rows: { slot: Slot; name: string; icon: JSX.Element; place: SavedPlace | null }[] = [
    { slot: 'home', name: t('addresses.home'), icon: <Home />, place: profile?.home ?? null },
    { slot: 'work', name: t('addresses.work'), icon: <Briefcase />, place: profile?.work ?? null },
  ];

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('addresses.title')} onBack={() => navigate('/profile')} />
      <p className="text-sm text-text-secondary">{t('addresses.intro')}</p>

      {error && <p className="text-sm text-danger">{error}</p>}
      {saved && <p className="text-sm text-success">{t('common.saved')}</p>}
      {!profile && !error && <SkeletonCard lines={3} label={t('addresses.loading')} />}

      {profile && (
        <Card className="divide-y divide-border p-0" data-testid="saved-addresses">
          {rows.map((row) => (
            <div key={row.slot} className="flex items-start gap-3 p-4" data-testid={`address-${row.slot}`}>
              <IconCircle tone="soft" size="sm" icon={row.icon} />
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-text-primary">{row.name}</p>
                {row.place ? (
                  <>
                    <p className="truncate text-xs text-text-secondary">{row.place.label}</p>
                    {row.place.lat == null && (
                      // Saved before places had coordinates: readable, not usable.
                      <p className="mt-1 text-xs font-medium text-accent-orange">{t('addresses.needsPin')}</p>
                    )}
                  </>
                ) : (
                  <p className="text-xs text-text-secondary">{t('addresses.notSet')}</p>
                )}
              </div>
              <div className="flex shrink-0 flex-col items-end gap-1">
                <Button size="md" variant="secondary" disabled={saving} onClick={() => setPicking(row.slot)}>
                  {row.place ? t('addresses.change') : t('addresses.set')}
                </Button>
                {row.place && (
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => save(row.slot, null)}
                    className="flex items-center gap-1 px-1 text-xs font-semibold text-danger"
                    data-testid={`remove-${row.slot}`}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    {t('addresses.remove')}
                  </button>
                )}
              </div>
            </div>
          ))}
        </Card>
      )}

      <div className="flex items-start gap-2 text-xs text-text-secondary">
        <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <p>{t('addresses.note')}</p>
      </div>

      <LocationPicker
        open={picking !== null}
        title={picking === 'work' ? t('addresses.setWork') : t('addresses.setHome')}
        allowCurrentLocation
        initialMode="search"
        markerKind="pickup"
        startAt={pickedStart(profile, picking)}
        onSelect={(address: GeoAddress) => {
          if (picking) void save(picking, { label: address.label, lat: address.lat, lng: address.lng });
        }}
        onClose={() => setPicking(null)}
      />
    </div>
  );
}

/** Opens the map where that address already is, rather than at the city again. */
function pickedStart(profile: CustomerProfileSummary | null, slot: Slot | null): GeoAddress | null {
  const place = slot === 'home' ? profile?.home : slot === 'work' ? profile?.work : null;
  if (!place || place.lat == null || place.lng == null) return null;
  return { label: place.label, lat: place.lat, lng: place.lng };
}
