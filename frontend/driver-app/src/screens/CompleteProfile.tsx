import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BrandHeader, ProfileCompletionForm } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { DriverProfileSummary, VehicleType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { markProfileComplete } from '../auth/ProtectedRoute';
import { VehicleFields } from '../components/VehicleFields';

/**
 * Required once, before the dashboard: name, date of birth (18 or over), a
 * photo, her vehicle, and optionally an email. New partners land here from
 * sign-in; partners from before these were required are sent here the next
 * time they open the app. The server refuses to save a profile without them.
 */
export function CompleteProfile() {
  const navigate = useNavigate();
  const { accountId } = useAuth();
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
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
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : 'Could not load your profile'));
  }, []);

  return (
    <div className="space-y-6 py-4">
      <BrandHeader size="md" />
      <div>
        <h1 className="font-heading text-2xl font-bold text-text-primary">Complete your profile</h1>
        <p className="mt-1 text-sm text-text-secondary">About you and your vehicle, before you can take trips.</p>
      </div>
      {loadError && <p className="text-sm text-danger">{loadError}</p>}
      {profile && (
        <ProfileCompletionForm
          initial={{ name: profile.name ?? '', dateOfBirth: profile.dateOfBirth ?? '', email: profile.email ?? '' }}
          photoUrl={profile.profilePhotoUrl}
          hasPhoto={profile.hasProfilePhoto}
          photoReason="Riders see your photo when you are on your way, so they know they have the right vehicle."
          submitLabel="Continue"
          extraFieldsValid={vehicle.registration.trim().length > 0}
          onUploadPhoto={async (file) => {
            try {
              setProfile(await usersApi.uploadMyPhoto(file));
            } catch (err) {
              throw new Error(err instanceof ApiError ? err.message : 'Could not upload that photo. Please try again.');
            }
          }}
          onSubmit={async (values) => {
            setShowVehicleErrors(true);
            try {
              await usersApi.updateMyProfile({
                name: values.name,
                dateOfBirth: values.dateOfBirth,
                email: values.email || undefined,
                vehicleType: vehicle.vehicleType,
                vehicleRegistrationNumber: vehicle.registration.trim(),
              });
            } catch (err) {
              throw new Error(err instanceof ApiError ? err.message : 'Could not save your profile. Please try again.');
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
        </ProfileCompletionForm>
      )}
    </div>
  );
}
