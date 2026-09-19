import { BadgeCheck, FileText, HelpCircle, Info, Lock, LogOut, MapPin, Receipt, ShieldAlert, User } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Card, ConfirmDialog, IconCircle, ListRow, PrivacyDataSection, TopHeader } from '@sheout/design-system';
import { ApiError, privacyApi, supportApi, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useTranslation } from '@sheout/design-system';

/**
 * REAL: name/phone fetched from GET /api/v1/users/customer/me. Log Out
 * clears the real session. The menu items below (Personal Details, Saved
 * Addresses, Payment History, Help & Support, About SheOut) have no
 * sub-screens - none were asked for in this pass - so they show a mock
 * "not implemented yet" instead of silently doing nothing. (Previously
 * these had a chevron implying they were tappable but no onClick at all -
 * that was the bug, not that they're unbuilt.)
 */
export function Profile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [confirmingLogout, setConfirmingLogout] = useState(false);
  const [grievanceEmail, setGrievanceEmail] = useState<string | null>(null);

  useEffect(() => {
    supportApi.getContact().then((c) => setGrievanceEmail(c.grievanceOfficerEmail)).catch(() => {});
  }, []);
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then(setProfile)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('profile.loadError')));
  }, []);

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('profile.title')} onBack={() => navigate('/home')} />

      <Card className="flex items-center gap-3">
        <Avatar url={profile?.profilePhotoUrl} name={profile?.name} size="lg" />
        <div>
          <p className="font-heading font-semibold text-text-primary">{profile?.name || t('profile.addName')}</p>
          <p className="text-sm text-text-secondary">
            {profile ? profile.phoneNumber || t('profile.googleSignIn') : error || t('common.loading')}
          </p>
        </div>
      </Card>

      {/* Every row used to sit in one undifferentiated card, and Identity
          Verification and Payments shared a single cell with no rule
          between them - so two unrelated things read as one control, and
          nothing on the screen said what any group was for. Grouped into
          named sections instead, the same heading-over-card shape Home and
          Wallet already use. */}
      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('profile.sectionAccount')}</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<User />} />} label={t('profile.personalDetails')} onClick={() => navigate('/profile/details')} />
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<MapPin />} />} label={t('addresses.title')} onClick={() => navigate('/profile/addresses')} />
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('profile.sectionSafety')}</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<BadgeCheck />} />} label={t('verification.title')} onClick={() => navigate('/verification')} />
          <ListRow icon={<IconCircle color="red" tone="soft" size="sm" icon={<ShieldAlert />} />} label={t('profile.emergencyContacts')} sublabel={t('profile.emergencyContactsSub')} onClick={() => navigate('/profile/emergency-contacts')} />
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('profile.sectionPayments')}</h2>
        <Card className="p-0">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<Receipt />} />} label={t('payments.history')} onClick={() => navigate('/profile/payments')} />
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-base font-semibold text-text-primary">{t('profile.sectionSupport')}</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<HelpCircle />} />} label={t('help.title')} onClick={() => navigate('/help')} />
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<Info />} />} label={t('about.title')} onClick={() => navigate('/about')} />
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<Lock />} />} label={t('legal.privacy')} onClick={() => navigate('/privacy')} />
          <ListRow icon={<IconCircle tone="soft" size="sm" icon={<FileText />} />} label={t('legal.terms')} onClick={() => navigate('/terms')} />
        </Card>
      </section>

      <PrivacyDataSection
        audience="customer"
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
