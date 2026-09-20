import { useEffect, useState } from 'react';
import { usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import type { SavedShortcut } from '../components/LocationPicker';
import { i18next } from '@sheout/design-system';

/**
 * Her Home and Work, ready to tap into a booking.
 * <p>
 * Only places that actually have a point come back: an address she typed as
 * free text before saved places had coordinates reads fine on her profile and
 * cannot be a pickup, so offering it here would be offering a dead end.
 * <p>
 * A failed fetch is silent. These are a shortcut, not the booking screen's
 * job; an error banner over a map because a convenience did not load would be
 * worse than the shortcut quietly not being there.
 */
export function useSavedPlaces(): SavedShortcut[] {
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);

  useEffect(() => {
    let cancelled = false;
    usersApi
      .getMyProfile()
      .then((p) => {
        if (!cancelled) setProfile(p);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  const shortcuts: SavedShortcut[] = [];
  const home = profile?.home;
  if (home?.lat != null && home.lng != null) {
    shortcuts.push({ kind: 'home', name: i18next.t('addresses.home'), address: { label: home.label, lat: home.lat, lng: home.lng } });
  }
  const work = profile?.work;
  if (work?.lat != null && work.lng != null) {
    shortcuts.push({ kind: 'work', name: i18next.t('addresses.work'), address: { label: work.label, lat: work.lat, lng: work.lng } });
  }
  return shortcuts;
}
