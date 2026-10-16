import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { BrandHeader, ConfirmDialog, ProfileCompletionForm, useBackGuard, ConsentCheckbox, ReferralCodeField, ReferralWelcome, clearPendingReferralCode, pendingReferralCode, type ReferralWelcomeDetails, openLegalDocument } from '@sheout/design-system';
import { apiErrorText } from '../lib/apiErrors';
import { ApiError, authApi, referralsApi, usersApi } from '../api/client';
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
  const { accountId, logout } = useAuth();
  /**
   * The way out. This is the first screen after sign-in, which replaced
   * itself, so the phone's back button used to close the app; and there was
   * no other way back to the number. Back now asks this, and so does the
   * link under the form.
   */
  const [leaving, setLeaving] = useState(false);
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  /**
   * Unticked until she ticks it. The server refuses to finish an account
   * without a consent record, so this is the ask, not the enforcement.
   */
  const [consent, setConsent] = useState(false);
  /** A friend's code, from her invite link or typed in. Offered only while the server says she can still use one. */
  const [referralCode, setReferralCode] = useState(pendingReferralCode());
  const [canUseReferral, setCanUseReferral] = useState(false);
  /**
   * Set once her code is accepted. Kept across a failed save, so pressing
   * Continue again does not send the code a second time - which the server
   * would refuse as already used, leaving her stuck on this screen.
   */
  const [joined, setJoined] = useState<ReferralWelcomeDetails | null>(null);
  /** The congratulations, shown once everything is saved. */
  const [celebrate, setCelebrate] = useState<ReferralWelcomeDetails | null>(null);
  useEffect(() => {
    referralsApi.mine().then((s) => setCanUseReferral(s.canApplyCode)).catch(() => setCanUseReferral(false));
  }, []);
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

  useBackGuard(() => setLeaving(true), celebrate === null);

  function switchNumber() {
    setLeaving(false);
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="space-y-6 py-4">
      <BrandHeader size="md" />
      <div>
        <h1 className="font-heading text-title text-text-primary">{t('completeProfile.title')}</h1>
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
          onAttemptSubmit={() => setShowVehicleErrors(true)}
          onUploadPhoto={async (file) => {
            try {
              setProfile(await usersApi.uploadMyPhoto(file));
            } catch (err) {
              throw new Error(err instanceof ApiError ? err.message : t('profile.uploadError'));
            }
          }}
          onSubmit={async (values) => {
            // The friend's code first: a wrong code is hers to fix before
            // anything is saved, and an empty field is simply skipped.
            let welcome = joined;
            if (!welcome && canUseReferral && referralCode.trim()) {
              try {
                welcome = await referralsApi.apply(referralCode.trim());
                setJoined(welcome);
                clearPendingReferralCode();
              } catch (err) {
                throw new Error(apiErrorText(err, 'refer.applyError'));
              }
            }
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
            if (welcome) setCelebrate(welcome);
            else navigate('/home', { replace: true });
          }}
        >
          <VehicleFields
            vehicleType={vehicle.vehicleType}
            registration={vehicle.registration}
            onChange={setVehicle}
            showErrors={showVehicleErrors}
          />
          {canUseReferral && <ReferralCodeField value={referralCode} onChange={setReferralCode} />}
          <ConsentCheckbox
            checked={consent}
            onChange={setConsent}
            error={showVehicleErrors && !consent ? ds('consent.required') : null}
            onOpenTerms={() => openLegalDocument('/terms')}
            onOpenPrivacy={() => openLegalDocument('/privacy')}
          />
        </ProfileCompletionForm>
      )}
      <button
        type="button"
        onClick={() => setLeaving(true)}
        className="mx-auto flex min-h-[44px] items-center px-4 text-sm font-semibold text-primary"
        data-testid="complete-profile-different-number"
      >
        {t('completeProfile.differentNumber')}
      </button>
      <ConfirmDialog
        open={leaving}
        title={t('completeProfile.leaveTitle')}
        message={t('completeProfile.leaveBody')}
        confirmLabel={t('completeProfile.leaveConfirm')}
        cancelLabel={t('completeProfile.stay')}
        onConfirm={switchNumber}
        onCancel={() => setLeaving(false)}
      />
      <ReferralWelcome details={celebrate} onContinue={() => navigate('/home', { replace: true })} />
    </div>
  );
}
