import { BadgeCheck, Camera, ChevronRight, MapPin, ShieldAlert } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Avatar,
  Card,
  IconCircle,
  ListRow,
  StatusBadge,
  TopHeader,
  phoneDisplay,
  useTranslation,
} from '@sheout/design-system';
import { useAppDrawer } from '../components/AppDrawer';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';

/**
 * Who she is on SheOut: her details (the card at the top), her ID check and
 * emergency contacts, and her saved addresses. Only what is about her -
 * what she does is in the menu (Safety Center, Refer, the Marketplace with
 * her shop and products, Help, Settings; see AppDrawer) and her money is the
 * Wallet tab, so every destination has one home.
 * <p>
 * The card at the top is her: tap it (or the camera on her photo) to change
 * her name, photo or email.
 */
export function Profile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const drawer = useAppDrawer();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('profile.loadError')));
  }, []);

  const icon = (node: JSX.Element, color?: 'red' | 'orange' | 'green') => <IconCircle tone="soft" size="sm" color={color} icon={node} />;

  return (
    <div className="space-y-6 pb-6">
      <TopHeader variant="plain" title={t('profile.title')} onMenuClick={drawer.open} />

      <Card className="p-0">
        <button
          type="button"
          onClick={() => navigate('/profile/details')}
          className="flex w-full items-center gap-4 p-4 text-left"
          aria-label={t('profile.editProfile')}
          data-testid="profile-card"
        >
          <span className="relative shrink-0">
            <Avatar url={profile?.profilePhotoUrl} name={profile?.name} size="lg" />
            <span className="absolute -bottom-0.5 -right-0.5 flex h-6 w-6 items-center justify-center rounded-full border-2 border-surface bg-primary text-text-inverse">
              <Camera className="h-3 w-3" aria-hidden="true" />
            </span>
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate font-heading text-card-title text-text-primary">
              {profile ? profile.name || t('profile.addName') : error || t('common.loading')}
            </span>
            <span className="block text-sm text-text-secondary">
              {profile ? (profile.phoneNumber ? phoneDisplay(profile.phoneNumber) : t('profile.googleSignIn')) : ''}
            </span>
            {profile && (
              <StatusBadge tone={profile.verified ? 'success' : 'warning'} className="mt-1">
                {profile.verified ? t('profile.verified') : t('profile.notVerified')}
              </StatusBadge>
            )}
          </span>
          <ChevronRight className="h-5 w-5 shrink-0 text-text-secondary" aria-hidden="true" />
        </button>
      </Card>

      <section>
        <h2 className="mb-3 font-heading text-section text-text-primary">{t('profile.sectionSafety')}</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow
            icon={icon(<BadgeCheck />, profile && !profile.verified ? 'orange' : undefined)}
            label={t('verification.title')}
            sublabel={profile ? (profile.verified ? t('profile.verified') : t('profile.verifyToRide')) : undefined}
            onClick={() => navigate('/verification')}
          />
          <ListRow icon={icon(<ShieldAlert />, 'red')} label={t('profile.emergencyContacts')} sublabel={t('profile.emergencyContactsSub')} onClick={() => navigate('/profile/emergency-contacts')} />
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-section text-text-primary">{t('profile.sectionAccount')}</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow icon={icon(<MapPin />)} label={t('addresses.title')} sublabel={t('profile.addressesSub')} onClick={() => navigate('/profile/addresses')} />
        </Card>
      </section>

    </div>
  );
}
