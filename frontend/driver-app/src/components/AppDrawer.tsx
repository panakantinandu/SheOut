import { FileText, Info, Languages, LifeBuoy, Lock, LogOut, Wallet } from 'lucide-react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Avatar,
  ConfirmDialog,
  Drawer,
  LANGUAGES,
  LanguagePicker,
  chooseLanguage,
  showToast,
  syncLanguageWithAccount,
  useAppLanguage,
  useTranslation,
  vehicleLabel,
} from '@sheout/design-system';
import { preferencesApi, usersApi } from '../api/client';
import type { DriverProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';

const APP_VERSION = '0.1.0';

interface DrawerControls {
  open: () => void;
}

const DrawerContext = createContext<DrawerControls>({ open: () => undefined });

/** Opens the menu drawer from anywhere below AppDrawerProvider. */
export function useAppDrawer(): DrawerControls {
  return useContext(DrawerContext);
}

/**
 * The partner app's menu. Language, payouts, support, the legal pages and
 * signing out - the things that are not a tab. No Seller or Refer entries:
 * those are rider features.
 * <p>
 * Also keeps the language in step with her account once she is signed in -
 * see syncLanguageWithAccount.
 */
export function AppDrawerProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isAuthenticated, logout } = useAuth();
  const lng = useAppLanguage();
  const [isOpen, setIsOpen] = useState(false);
  const [pickingLanguage, setPickingLanguage] = useState(false);
  const [confirmingLogout, setConfirmingLogout] = useState(false);
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);

  useEffect(() => {
    if (!isAuthenticated) return;
    syncLanguageWithAccount(preferencesApi).catch(() => undefined);
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isOpen || !isAuthenticated) return;
    usersApi.getMyProfile().then(setProfile).catch(() => undefined);
  }, [isOpen, isAuthenticated]);

  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);
  const go = (path: string) => {
    setIsOpen(false);
    navigate(path);
  };
  const controls = useMemo(() => ({ open }), [open]);
  const languageName = LANGUAGES.find((l) => l.code === lng)?.nativeName ?? 'English';

  return (
    <DrawerContext.Provider value={controls}>
      {children}
      <Drawer
        open={isOpen}
        onClose={close}
        header={
          <div className="flex items-center gap-3">
            <Avatar url={profile?.profilePhotoUrl} name={profile?.name} size="lg" />
            <div className="min-w-0">
              <p className="truncate font-heading text-lg font-semibold">{profile?.name || t('profile.addName')}</p>
              <p className="truncate text-sm opacity-85">
                {profile ? `${t('drawer.partner')} · ${vehicleLabel(profile.vehicleType)}` : t('drawer.partner')}
              </p>
            </div>
          </div>
        }
        sections={[
          {
            key: 'main',
            items: [
              {
                key: 'language',
                label: t('drawer.language'),
                sublabel: languageName,
                icon: <Languages />,
                onClick: () => {
                  setIsOpen(false);
                  setPickingLanguage(true);
                },
              },
              { key: 'payouts', label: t('drawer.payouts'), icon: <Wallet />, onClick: () => go('/payouts') },
            ],
          },
          {
            key: 'info',
            title: t('drawer.helpAndInfo'),
            items: [
              { key: 'support', label: t('drawer.support'), icon: <LifeBuoy />, onClick: () => go('/help') },
              { key: 'about', label: t('about.title'), icon: <Info />, onClick: () => go('/about') },
              { key: 'privacy', label: t('legal.privacy'), icon: <Lock />, onClick: () => go('/privacy') },
              { key: 'terms', label: t('legal.terms'), icon: <FileText />, onClick: () => go('/terms') },
            ],
          },
          {
            key: 'account',
            items: [
              {
                key: 'logout',
                label: t('auth.logout'),
                icon: <LogOut />,
                tone: 'danger',
                onClick: () => {
                  setIsOpen(false);
                  setConfirmingLogout(true);
                },
              },
            ],
          },
        ]}
        footer={t('drawer.version', { version: APP_VERSION })}
      />
      <LanguagePicker
        open={pickingLanguage}
        onClose={() => setPickingLanguage(false)}
        onSelect={async (code) => {
          setPickingLanguage(false);
          const saved = await chooseLanguage(code, isAuthenticated ? preferencesApi : undefined);
          showToast(saved ? t('drawer.languageSaved') : t('drawer.languageSavedLocally'));
        }}
      />
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
    </DrawerContext.Provider>
  );
}
