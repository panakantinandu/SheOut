import { Bike, Camera, Landmark, LogOut, ShieldCheck, User } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Avatar,
  Button,
  Card,
  ConfirmDialog,
  IconCircle,
  ListRow,
  PrivacyDataSection,
  StatusBadge,
  TopHeader,
  vehicleLabel,
  ProfileCompletionForm,
  shrinkPhoto,
} from '@sheout/design-system';
import { useAppDrawer } from '../components/AppDrawer';
import { ApiError, privacyApi, supportApi, usersApi } from '../api/client';
import type { DriverProfileSummary, VehicleType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { VehicleFields } from '../components/VehicleFields';
import { useTranslation } from '@sheout/design-system';

/**
 * REAL: profile fetched from GET /api/v1/users/driver/me, edits saved via
 * PUT (same endpoint). Log Out clears the real session. Help & Support and
 * the notification bell are real screens now - the placeholders they used
 * to show are gone.
 */
export function Profile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const drawer = useAppDrawer();
  const { logout } = useAuth();
  const [confirmingLogout, setConfirmingLogout] = useState(false);
  const [grievanceEmail, setGrievanceEmail] = useState<string | null>(null);

  useEffect(() => {
    supportApi.getContact().then((c) => setGrievanceEmail(c.grievanceOfficerEmail)).catch(() => {});
  }, []);
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

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="plain" title={t('profile.title')} onMenuClick={drawer.open} />

      <Card className="flex items-center gap-3">
        <Avatar url={profile?.profilePhotoUrl} name={profile?.name} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="font-heading font-semibold text-text-primary">
            {profile ? profile.name || t('profile.addName') : error || t('common.loading')}
          </p>
          <p className="text-sm text-text-secondary">{profile?.phoneNumber ?? ''}</p>
          {profile && (
            <StatusBadge tone={profile.verified ? 'success' : 'warning'} className="mt-1">
              {profile.verified ? t('profile.verified') : t('profile.unverified')}
            </StatusBadge>
          )}
        </div>
      </Card>

      {/* The photo step, shown first and loudest when it is missing, because
          it is what stands between her and her first booking. Riders check
          the face on their screen against the person at the kerb; it is the
          one thing that tells her she has the right vehicle. */}
      {profile && !editing && (
        <Card tone={profile.hasProfilePhoto ? 'default' : 'warning'} className="space-y-3">
          <div className="flex items-start gap-3">
            <IconCircle
              tone="soft"
              color={profile.hasProfilePhoto ? undefined : 'orange'}
              icon={profile.hasProfilePhoto ? <User /> : <Camera />}
            />
            <div className="flex-1">
              <p className="font-heading font-semibold text-text-primary">
                {profile.hasProfilePhoto ? t('profile.yourPhoto') : t('profile.addPhotoToGoOnline')}
              </p>
              <p className="mt-1 text-sm text-text-secondary">
                {profile.hasProfilePhoto
                  ? t('profile.photoReason')
                  : t('profile.photoRequired')}
              </p>
            </div>
          </div>
          <input
            ref={photoInputRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={handlePhotoSelected}
          />
          <Button
            fullWidth
            variant={profile.hasProfilePhoto ? 'secondary' : 'primary'}
            icon={<Camera className="h-4 w-4" />}
            disabled={uploadingPhoto}
            onClick={() => photoInputRef.current?.click()}
          >
            {/* hasProfilePhoto, not the URL: on local-disk storage a photo
                that exists still has no displayable URL, and keying the
                label off the URL would offer "Add photo" to somebody who
                already added one. */}
            {uploadingPhoto
              ? t('common.uploading')
              : profile.hasProfilePhoto
                ? t('profile.changePhoto')
                : t('profile.addPhoto')}
          </Button>
        </Card>
      )}

      {error && <p className="text-sm text-danger">{error}</p>}

      {profile && !editing && (
        <Card className="space-y-3">
          {/* Headed like the photo card above it, rather than two bare
              label-value lines - see the Part E polish pass. */}
          <div className="flex items-center gap-3">
            <IconCircle tone="soft" color="orange" icon={<Bike />} />
            <p className="font-heading font-semibold text-text-primary">{t('profile.yourVehicle')}</p>
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

      <Card className="divide-y divide-border p-0">
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<ShieldCheck />} />}
          label={t('verification.title')}
          onClick={() => navigate('/verification')}
        />
        <ListRow
          icon={<IconCircle tone="soft" size="sm" icon={<Landmark />} />}
          label={t('profile.payouts')}
          onClick={() => navigate('/payouts')}
        />
      </Card>

      <PrivacyDataSection
        audience="driver"
        grievanceOfficerEmail={grievanceEmail}
        onDownload={() => privacyApi.downloadMyData()}
        onDelete={async () => {
          await privacyApi.deleteAccount();
        }}
        onDeleted={() => {
          logout();
          navigate('/login', {
            replace: true,
            state: { notice: t('profile.deletedNotice') },
          });
        }}
      />

      <Card className="p-0">
        <ListRow
          icon={<IconCircle color="red" tone="soft" size="sm" icon={<LogOut />} />}
          label={t('auth.logout')}
          chevron={false}
          onClick={() => setConfirmingLogout(true)}
        />
      </Card>

      {/* Logging out used to fire on a single tap of the row above, with
          no way back. */}
      <ConfirmDialog
        open={confirmingLogout}
        title={t('auth.logoutTitle')}
        message={t('auth.logoutMessage')}
        confirmLabel={t('auth.logout')}
        destructive
        onConfirm={handleLogout}
        onCancel={() => setConfirmingLogout(false)}
      />
    </div>
  );
}
