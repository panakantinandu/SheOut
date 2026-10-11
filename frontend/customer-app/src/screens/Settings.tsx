import { FileText, Info, Languages, Lock, LogOut, Smartphone, Star } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Card,
  ConfirmDialog,
  IconCircle,
  LANGUAGES,
  LanguagePicker,
  ListRow,
  PrivacyDataSection,
  ThemeToggle,
  TopHeader,
  chooseLanguage,
  showToast,
  useAppLanguage,
  useTranslation,
} from '@sheout/design-system';
import { preferencesApi, privacyApi, supportApi } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useGoBack } from '../lib/useGoBack';
import { APP_VERSION } from '../lib/appVersion';

/** Where "Rate the App" goes. Empty until SheOut has a store listing; until then the row says so. */
const STORE_URL = import.meta.env.VITE_APP_STORE_URL as string | undefined;

/**
 * Everything about how the app behaves rather than who she is: language,
 * appearance, where she is signed in, her data, the legal pages - and the
 * one place to log out. Profile is who she is; this is the app.
 * <p>
 * Log Out used to be in two places (the menu and the foot of Profile), and
 * language only in the menu, so the screen people look in for settings did
 * not have them all. Now each lives here and nowhere else.
 */
export function Settings() {
  const { t } = useTranslation();
  const { t: ds } = useTranslation('ds');
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const goBack = useGoBack('/profile');
  const { isAuthenticated, logout } = useAuth();
  const lng = useAppLanguage();
  const [pickingLanguage, setPickingLanguage] = useState(false);
  const [confirmingLogout, setConfirmingLogout] = useState(false);
  const [grievanceEmail, setGrievanceEmail] = useState<string | null>(null);

  useEffect(() => {
    supportApi.getContact().then((c) => setGrievanceEmail(c.grievanceOfficerEmail)).catch(() => {});
  }, []);

  const languageName = LANGUAGES.find((l) => l.code === lng)?.nativeName ?? 'English';
  const row = (icon: JSX.Element, red = false) => <IconCircle tone="soft" size="sm" color={red ? 'red' : undefined} icon={icon} />;

  return (
    <div className="space-y-6 pb-6" data-testid="settings-screen">
      <TopHeader variant="back" title={t('settings.title')} onBack={goBack} />

      <section>
        <h2 className="mb-3 font-heading text-section text-text-primary">{t('settings.preferences')}</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow icon={row(<Languages />)} label={t('drawer.language')} sublabel={languageName} onClick={() => setPickingLanguage(true)} />
          {/* Per device, not per account: the right answer in bed on a phone is not the right answer at a desk. */}
          <div className="space-y-3 px-4 py-4" data-testid="appearance">
            <div>
              <p className="font-medium text-text-primary">{ds('theme.label')}</p>
              <p className="text-sm text-text-secondary">{ds('theme.description')}</p>
            </div>
            <ThemeToggle />
          </div>
        </Card>
      </section>

      <section>
        <h2 className="mb-3 font-heading text-section text-text-primary">{t('settings.security')}</h2>
        <Card className="p-0">
          <ListRow icon={row(<Smartphone />)} label={t('devices.title')} sublabel={t('devices.sub')} onClick={() => navigate('/profile/devices')} />
        </Card>
      </section>

      <PrivacyDataSection
        startWithDelete={params.get('delete') === '1'}
        audience="customer"
        grievanceOfficerEmail={grievanceEmail}
        onDownload={() => privacyApi.downloadMyData()}
        onDelete={async () => {
          await privacyApi.deleteAccount();
        }}
        onDeleted={() => {
          logout();
          navigate('/login', { replace: true, state: { notice: t('profile.deletedNotice') } });
        }}
      />

      <section>
        <h2 className="mb-3 font-heading text-section text-text-primary">{t('settings.aboutLegal')}</h2>
        <Card className="divide-y divide-border p-0">
          <ListRow icon={row(<Info />)} label={t('about.title')} onClick={() => navigate('/about')} />
          <ListRow icon={row(<Lock />)} label={t('legal.privacy')} onClick={() => navigate('/privacy')} />
          <ListRow icon={row(<FileText />)} label={t('legal.terms')} onClick={() => navigate('/terms')} />
          {/* Only once there is a store listing to rate: until then the row
              led nowhere but a "not yet" toast. */}
          {STORE_URL && (
            <ListRow icon={row(<Star />)} label={t('drawer.rate')} onClick={() => window.open(STORE_URL, '_blank', 'noopener')} />
          )}
        </Card>
      </section>

      <Card className="p-0">
        <ListRow icon={row(<LogOut />, true)} label={t('auth.logout')} chevron={false} onClick={() => setConfirmingLogout(true)} />
      </Card>
      <p className="text-center text-caption text-text-secondary">{t('drawer.version', { version: APP_VERSION })}</p>

      <LanguagePicker
        open={pickingLanguage}
        onClose={() => setPickingLanguage(false)}
        onSelect={async (code) => {
          setPickingLanguage(false);
          const saved = await chooseLanguage(code, isAuthenticated ? preferencesApi : undefined);
          showToast(saved ? t('drawer.languageSaved') : t('drawer.languageSavedLocally'));
        }}
      />
      {/* Logging out asks first: one stray tap should not sign her out mid-trip. */}
      <ConfirmDialog
        open={confirmingLogout}
        title={t('auth.logoutTitle')}
        message={t('auth.logoutMessage')}
        confirmLabel={t('auth.logout')}
        destructive
        onConfirm={() => {
          setConfirmingLogout(false);
          logout();
          navigate('/login', { replace: true });
        }}
        onCancel={() => setConfirmingLogout(false)}
      />
    </div>
  );
}
