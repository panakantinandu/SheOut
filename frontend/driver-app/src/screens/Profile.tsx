import { Camera, Car, Landmark, ShieldCheck } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Button, Card, IconCircle, ListRow, StatusBadge, TopHeader, phoneDisplay, vehicleLabel, ProfileCompletionForm, shrinkPhoto, ServiceArt } from '@sheout/design-system';
import { useAppDrawer } from '../components/AppDrawer';
import { ApiError, usersApi } from '../api/client';
import type { DriverProfileSummary, VehicleType } from '../api/types';
import { VehicleFields } from '../components/VehicleFields';
import { PendingChangeCard } from '../components/PendingChangeCard';
import { YourCoverCard } from '../components/YourCoverCard';
import { useTranslation } from '@sheout/design-system';

/**
 * Who she is on SheOut and her work: her photo and details, her vehicle,
 * verification and payouts. Refer a Friend, Help and Settings are in the
 * menu (see AppDrawer), so nothing is in both.
 * <p>
 * Her photo is changed by tapping it, the way every app does it. A big
 * "Your photo" card used to sit first on this screen even when she had a
 * photo; it now appears only while the photo is missing - the one time it
 * is the most important thing here, because she cannot go online without it.
 */
export function Profile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const drawer = useAppDrawer();
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [vehicle, setVehicle] = useState<{ vehicleType: VehicleType; registration: string }>({
    vehicleType: 'BIKE',
    registration: '',
  });
  const [showVehicleErrors, setShowVehicleErrors] = useState(false);
  const photoInputRef = useRef<HTMLInputElement>(null);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);

  async function handlePhotoSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    // Cleared immediately so picking the same file twice still fires.
    e.target.value = '';
    if (!file) return;
    setUploadingPhoto(true);
    setError(null);
    try {
      setProfile(await usersApi.uploadMyPhoto(await shrinkPhoto(file)));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : t('profile.uploadError'));
    } finally {
      setUploadingPhoto(false);
    }
  }

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then((p) => {
        setProfile(p);
        setVehicle({ vehicleType: p.vehicleType ?? 'BIKE', registration: p.vehicleRegistrationNumber ?? '' });
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('profile.loadError')));
  }, []);

  const icon = (node: JSX.Element) => <IconCircle tone="soft" size="sm" icon={node} />;

  return (
    <div className="space-y-6 pb-6">
      <TopHeader variant="plain" title={t('profile.title')} onMenuClick={drawer.open} />

      <input ref={photoInputRef} type="file" accept="image/*" className="hidden" onChange={handlePhotoSelected} />
      <Card className="flex items-center gap-4">
        <button
          type="button"
          className="relative shrink-0 rounded-full"
          onClick={() => photoInputRef.current?.click()}
          disabled={!profile || uploadingPhoto}
          aria-label={profile?.hasProfilePhoto ? t('profile.changePhoto') : t('profile.addPhoto')}
          data-testid="avatar-photo"
        >
          <Avatar url={profile?.profilePhotoUrl} name={profile?.name} size="lg" className={uploadingPhoto ? 'opacity-50' : undefined} />
          <span className="absolute -bottom-0.5 -right-0.5 flex h-6 w-6 items-center justify-center rounded-full border-2 border-surface bg-primary text-text-inverse">
            <Camera className="h-3 w-3" aria-hidden="true" />
          </span>
        </button>
        <div className="min-w-0 flex-1">
          <p className="truncate font-heading text-card-title text-text-primary">
            {profile ? profile.name || t('profile.addName') : error || t('common.loading')}
          </p>
          <p className="text-sm text-text-secondary">{phoneDisplay(profile?.phoneNumber)}</p>
          {profile && (
            <StatusBadge tone={profile.verified ? 'success' : 'warning'} className="mt-1">
              {profile.verified ? t('profile.verified') : t('profile.unverified')}
            </StatusBadge>
          )}
        </div>
      </Card>

      {/* Only while her photo is missing: then it is what stands between her
          and her first booking. Riders check the face on their screen against
          the person at the kerb. hasProfilePhoto, not the URL: on local-disk
          storage a photo that exists still has no displayable URL. */}
      {profile && !editing && !profile.hasProfilePhoto && (
        <Card tone="warning" className="space-y-3" data-testid="photo-missing">
          <div className="flex items-start gap-3">
            <IconCircle tone="soft" color="orange" icon={<Camera />} />
            <div className="flex-1">
              <p className="font-heading text-card-title text-text-primary">{t('profile.addPhotoToGoOnline')}</p>
              <p className="mt-1 text-sm text-text-secondary">{t('profile.photoRequired')}</p>
            </div>
          </div>
          <Button fullWidth icon={<Camera className="h-4 w-4" />} disabled={uploadingPhoto} onClick={() => photoInputRef.current?.click()}>
            {uploadingPhoto ? t('common.uploading') : t('profile.addPhoto')}
          </Button>
        </Card>
      )}

      {/* Health, life or accident cover she is enrolled in - only once the insurer has confirmed her. */}
      <YourCoverCard />

      {error && <p className="text-sm text-danger">{error}</p>}

      {/* A change to what riders see, waiting for SheOut's team - see PendingChangeCard. */}
      {profile?.pendingChange && !editing && <PendingChangeCard change={profile.pendingChange} onUpdated={setProfile} />}

      {profile && !editing && (
        <Card className="space-y-3">
          {/* Headed like the photo card above it, rather than two bare
              label-value lines - see the Part E polish pass. */}
          <div className="flex items-center gap-3">
            {profile.vehicleType === 'BIKE' || !profile.vehicleType ? (
              <ServiceArt kind="ride" size="md" />
            ) : (
              <IconCircle tone="soft" color="orange" icon={<Car />} />
            )}
            <p className="font-heading text-card-title text-text-primary">{t('profile.yourVehicle')}</p>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-text-secondary">{t('profile.vehicle')}</span>
            <span className="font-medium text-text-primary">{vehicleLabel(profile.vehicleType)}</span>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-text-secondary">{t('profile.registrationNo')}</span>
            <span className="font-medium text-text-primary">{profile.vehicleRegistrationNumber ?? t('profile.notSet')}</span>
          </div>
          <Button variant="secondary" fullWidth onClick={() => setEditing(true)}>
            {t('profile.editDetails')}
          </Button>
        </Card>
      )}

      {profile && editing && (
        <Card className="space-y-4">
          <ProfileCompletionForm
            initial={{ name: profile.name ?? '', dateOfBirth: profile.dateOfBirth ?? '', email: profile.email ?? '' }}
            photoUrl={profile.profilePhotoUrl}
            hasPhoto={profile.hasProfilePhoto}
            photoReason={t('profile.photoReason')}
            submitLabel={t('common.save')}
            extraFieldsValid={vehicle.registration.trim().length > 0}
            onUploadPhoto={async (file) => {
              try {
                setProfile(await usersApi.uploadMyPhoto(file));
              } catch (err) {
                throw new Error(err instanceof ApiError ? err.message : t('profile.uploadError'));
              }
            }}
            onSubmit={async (values) => {
              setShowVehicleErrors(true);
              try {
                setProfile(
                  await usersApi.updateMyProfile({
                    name: values.name,
                    dateOfBirth: values.dateOfBirth,
                    email: values.email || undefined,
                    vehicleType: vehicle.vehicleType,
                    vehicleRegistrationNumber: vehicle.registration.trim(),
                  })
                );
                setEditing(false);
              } catch (err) {
                throw new Error(err instanceof ApiError ? err.message : t('profile.saveError'));
              }
            }}
          >
            <VehicleFields
              vehicleType={vehicle.vehicleType}
              registration={vehicle.registration}
              onChange={setVehicle}
              showErrors={showVehicleErrors}
            />
          </ProfileCompletionForm>
          <Button variant="secondary" fullWidth onClick={() => setEditing(false)}>
            {t('common.cancel')}
          </Button>
        </Card>
      )}

      {!editing && (
        <>
          <section>
            <h2 className="mb-3 font-heading text-section text-text-primary">{t('profile.sectionWork')}</h2>
            <Card className="divide-y divide-border p-0">
              <ListRow icon={icon(<ShieldCheck />)} label={t('verification.title')} sublabel={t('profile.verificationSub')} onClick={() => navigate('/verification')} />
              <ListRow icon={icon(<Landmark />)} label={t('profile.payouts')} sublabel={t('profile.payoutsSub')} onClick={() => navigate('/payouts')} />
            </Card>
          </section>
        </>
      )}
    </div>
  );
}
