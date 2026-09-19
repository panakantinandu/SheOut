import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, ProfileCompletionForm, SkeletonCard, TopHeader } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useTranslation } from '@sheout/design-system';

/**
 * Name, date of birth, photo and email - the same form and the same rules as
 * the first-run completion screen, so editing can never save a profile that
 * completing could not.
 * <p>
 * Phone number is display-only: it is the account identity in auth (one
 * phone, one account, one role) and this endpoint cannot change it. Home and
 * work addresses live on the same endpoint but get their own screen - see
 * SavedAddresses - and are passed back unchanged here, because PUT replaces
 * the whole profile.
 */
export function PersonalDetails() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('profile.loadError')));
  }, []);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('profile.personalDetails')} onBack={() => navigate('/profile')} />
      {error && <p className="text-sm text-danger">{error}</p>}
      {saved && <p className="text-sm text-success">{t('common.saved')}</p>}

      {!profile ? (
        !error && <SkeletonCard lines={3} label={t('profile.loading')} />
      ) : (
        <Card className="space-y-4">
          <ProfileCompletionForm
            initial={{ name: profile.name ?? '', dateOfBirth: profile.dateOfBirth ?? '', email: profile.email ?? '' }}
            photoUrl={profile.profilePhotoUrl}
            hasPhoto={profile.hasProfilePhoto}
            photoReason={t('profile.photoReason')}
            submitLabel={t('common.saveChanges')}
            onUploadPhoto={async (file) => {
              try {
                setProfile(await usersApi.uploadMyPhoto(file));
              } catch (err) {
                throw new Error(err instanceof ApiError ? err.message : t('profile.uploadError'));
              }
            }}
            onSubmit={async (values) => {
              setSaved(false);
              try {
                setProfile(
                  await usersApi.updateMyProfile({
                    name: values.name,
                    dateOfBirth: values.dateOfBirth,
                    email: values.email || undefined,
                    homeAddress: profile.homeAddress ?? undefined,
                    workAddress: profile.workAddress ?? undefined,
                  })
                );
                setSaved(true);
              } catch (err) {
                throw new Error(err instanceof ApiError ? err.message : t('profile.saveError'));
              }
            }}
          >
            <div>
              <p className="text-sm text-text-secondary">{t('profile.mobile')}</p>
              <p className="text-text-primary">{profile.phoneNumber}</p>
              <p className="mt-1 text-xs text-text-secondary">
                {t('profile.mobileFixed')}
              </p>
            </div>
          </ProfileCompletionForm>
        </Card>
      )}
    </div>
  );
}
