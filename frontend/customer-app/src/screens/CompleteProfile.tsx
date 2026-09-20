import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BrandHeader, ProfileCompletionForm, ConsentCheckbox } from '@sheout/design-system';
import { ApiError, authApi, usersApi } from '../api/client';
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
  const { t: ds } = useTranslation('ds');
  const notice = (useLocation().state as { notice?: string } | null)?.notice ?? null;
  const navigate = useNavigate();
  const { accountId } = useAuth();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  /**
   * Unticked until she ticks it. The server refuses to finish an account
   * without a consent record, so this is the ask, not the enforcement.
   */
  const [consent, setConsent] = useState(false);

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
        {notice && (
          <p className="mt-3 rounded-input bg-primary-light px-4 py-3 text-sm font-medium text-primary" data-testid="new-account-notice">
            {notice}
          </p>
        )}
      </div>
      {loadError && <p className="text-sm text-danger">{loadError}</p>}
      {profile && (
        <ProfileCompletionForm
          extraFieldsValid={consent}
          extraFieldsProblem={ds('consent.required')}
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
              await authApi.acceptConsent();
              await usersApi.updateMyProfile({
                name: values.name,
                dateOfBirth: values.dateOfBirth,
                email: values.email || undefined,
                // Passed through untouched: this screen is not where saved
                // places are edited - see SavedAddresses.
                home: profile.home,
                work: profile.work,
              });
            } catch (err) {
              throw new Error(err instanceof ApiError ? err.message : t('profile.saveError'));
            }
            if (accountId) markProfileComplete(accountId);
            // Straight into the introduction: this is her first moment in the
            // app, and it is the only time it is shown.
            navigate('/welcome', { replace: true });
          }}
        >
          <ConsentCheckbox
            checked={consent}
            onChange={setConsent}
            onOpenTerms={() => navigate('/terms')}
            onOpenPrivacy={() => navigate('/privacy')}
          />
        </ProfileCompletionForm>
      )}
    </div>
  );
}
