import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BrandHeader, ProfileCompletionForm, ConsentCheckbox } from '@sheout/design-system';
import { ApiError, authApi, usersApi } from '../api/client';
import type { DriverProfileSummary, VehicleType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { markProfileComplete } from '../auth/ProtectedRoute';
import { VehicleFields } from '../components/VehicleFields';
import { useTranslation } from '@sheout/design-system';

/**
 * Required once, before the dashboard: name, date of birth (18 or over), a
 * photo, her vehicle, and optionally an email. New partners land here from
 * sign-in; partners from before these were required are sent here the next
 * time they open the app. The server refuses to save a profile without them.
 */
export function CompleteProfile() {
  const { t } = useTranslation();
  const { t: ds } = useTranslation('ds');
  const notice = (useLocation().state as { notice?: string } | null)?.notice ?? null;
  const navigate = useNavigate();
  const { accountId } = useAuth();
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  /**
   * Unticked until she ticks it. The server refuses to finish an account
   * without a consent record, so this is the ask, not the enforcement.
   */
  const [consent, setConsent] = useState(false);
  const [vehicle, setVehicle] = useState<{ vehicleType: VehicleType; registration: string }>({
    vehicleType: 'BIKE',
    registration: '',
  });
  const [showVehicleErrors, setShowVehicleErrors] = useState(false);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then((p) => {
        setProfile(p);
        setVehicle({ vehicleType: p.vehicleType ?? 'BIKE', registration: p.vehicleRegistrationNumber ?? '' });
      })
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
          initial={{ name: profile.name ?? '', dateOfBirth: profile.dateOfBirth ?? '', email: profile.email ?? '' }}
          photoUrl={profile.profilePhotoUrl}
          hasPhoto={profile.hasProfilePhoto}
          photoReason={t('profile.photoReason')}
          submitLabel={t('common.continue')}
          extraFieldsValid={vehicle.registration.trim().length > 0 && consent}
          extraFieldsProblem={vehicle.registration.trim().length === 0 ? null : ds('consent.required')}
          onAttemptSubmit={() => setShowVehicleErrors(true)}
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
                vehicleType: vehicle.vehicleType,
                vehicleRegistrationNumber: vehicle.registration.trim(),
              });
            } catch (err) {
              throw new Error(err instanceof ApiError ? err.message : t('profile.saveError'));
            }
            if (accountId) markProfileComplete(accountId);
            navigate('/home', { replace: true });
          }}
        >
          <VehicleFields
            vehicleType={vehicle.vehicleType}
            registration={vehicle.registration}
            onChange={setVehicle}
            showErrors={showVehicleErrors}
          />
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
