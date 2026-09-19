import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrandHeader, ProfileCompletionForm } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { markProfileComplete } from '../auth/ProtectedRoute';
import { useTranslation } from '@sheout/design-system';

/**
 * Required once, before the app: name, date of birth (18 or over), a photo,
 * and optionally an email. New accounts land here from sign-in; accounts
 * from before these were required are sent here the next time they open the
 * app. The server refuses to save a profile without them, so this screen is
 * the explanation, not the enforcement.
 */
export function CompleteProfile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { accountId } = useAuth();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t('profile.loadError')));
  }, []);

  return (
    <div className="space-y-6 py-4">
      <BrandHeader size="md" />
      <div>
        <h1 className="font-heading text-2xl font-bold text-text-primary">{t('completeProfile.title')}</h1>
        <p className="mt-1 text-sm text-text-secondary">{t('completeProfile.subtitle')}</p>
      </div>
      {loadError && <p className="text-sm text-danger">{loadError}</p>}
      {profile && (
        <ProfileCompletionForm
          initial={{ name: profile.name ?? '', dateOfBirth: profile.dateOfBirth ?? '', email: profile.email ?? '' }}
          photoUrl={profile.profilePhotoUrl}
          hasPhoto={profile.hasProfilePhoto}
          photoReason={t('profile.photoReason')}
          submitLabel={t('common.continue')}
          onUploadPhoto={async (file) => {
            try {
              setProfile(await usersApi.uploadMyPhoto(file));
            } catch (err) {
              throw new Error(err instanceof ApiError ? err.message : t('profile.uploadError'));
            }
          }}
          onSubmit={async (values) => {
            try {
              await usersApi.updateMyProfile({
                name: values.name,
                dateOfBirth: values.dateOfBirth,
                email: values.email || undefined,
                homeAddress: profile.homeAddress ?? undefined,
                workAddress: profile.workAddress ?? undefined,
              });
            } catch (err) {
              throw new Error(err instanceof ApiError ? err.message : t('profile.saveError'));
            }
            if (accountId) markProfileComplete(accountId);
            navigate('/home', { replace: true });
          }}
        />
      )}
    </div>
  );
}
