import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, ProfileCompletionForm, SkeletonCard, TopHeader } from '@sheout/design-system';
import { ApiError, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';

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
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load profile'));
  }, []);

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Personal Details" onBack={() => navigate('/profile')} />
      {error && <p className="text-sm text-danger">{error}</p>}
      {saved && <p className="text-sm text-success">Saved.</p>}

      {!profile ? (
        !error && <SkeletonCard lines={3} label="Loading your details" />
      ) : (
        <Card className="space-y-4">
          <ProfileCompletionForm
            initial={{ name: profile.name ?? '', dateOfBirth: profile.dateOfBirth ?? '', email: profile.email ?? '' }}
            photoUrl={profile.profilePhotoUrl}
            hasPhoto={profile.hasProfilePhoto}
            photoReason="Your partner sees your photo so she knows she is picking up the right person."
            submitLabel="Save Changes"
            onUploadPhoto={async (file) => {
              try {
                setProfile(await usersApi.uploadMyPhoto(file));
              } catch (err) {
                throw new Error(err instanceof ApiError ? err.message : 'Could not upload that photo.');
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
                throw new Error(err instanceof ApiError ? err.message : 'Could not save changes');
              }
            }}
          >
            <div>
              <p className="text-sm text-text-secondary">Mobile Number</p>
              <p className="text-text-primary">{profile.phoneNumber}</p>
              <p className="mt-1 text-xs text-text-secondary">
                Your number identifies your account and cannot be changed here.
              </p>
            </div>
          </ProfileCompletionForm>
        </Card>
      )}
    </div>
  );
}
