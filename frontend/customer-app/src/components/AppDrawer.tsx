import {
  FileText,
  Gift,
  Info,
  Languages,
  LifeBuoy,
  Lock,
  LogOut,
  Moon,
  Star,
  Store,
} from 'lucide-react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Avatar,
  ConfirmDialog,
  Drawer,
  LANGUAGES,
  LanguagePicker,
  ThemePicker,
  chooseLanguage,
  showToast,
  syncLanguageWithAccount,
  useAppLanguage,
  useTheme,
  useTranslation,
} from '@sheout/design-system';
import { preferencesApi, usersApi } from '../api/client';
import type { CustomerProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';

/**
 * Where "Rate the App" goes. Empty until SheOut has a store listing; until
 * then the row says so instead of opening nothing.
 */
const STORE_URL = import.meta.env.VITE_APP_STORE_URL as string | undefined;
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
 * The rider app's menu, and the language picker it opens.
 * <p>
 * Also where the language is kept in step with her account: once she is
 * signed in, a choice made on this phone is saved to the account, and
 * otherwise the account's choice is applied - see syncLanguageWithAccount.
 */
export function AppDrawerProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const { t: ds } = useTranslation('ds');
  const navigate = useNavigate();
  const { isAuthenticated, logout } = useAuth();
  const lng = useAppLanguage();
  const [isOpen, setIsOpen] = useState(false);
  const [pickingLanguage, setPickingLanguage] = useState(false);
  const [pickingTheme, setPickingTheme] = useState(false);
  const [themeChoice] = useTheme();
  const [confirmingLogout, setConfirmingLogout] = useState(false);
  const [profile, setProfile] = useState<CustomerProfileSummary | null>(null);

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
              <p className="truncate text-sm opacity-85">{profile?.phoneNumber ?? ''}</p>
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
              {
                key: 'theme',
                label: ds('theme.label'),
                sublabel: ds(`theme.${themeChoice}`),
                icon: <Moon />,
                onClick: () => {
                  setIsOpen(false);
                  setPickingTheme(true);
                },
              },
              { key: 'refer', label: t('drawer.refer'), sublabel: t('drawer.comingSoon'), icon: <Gift />, onClick: () => go('/refer') },
              { key: 'seller', label: t('drawer.seller'), sublabel: t('drawer.comingSoon'), icon: <Store />, onClick: () => go('/seller') },
              {
                key: 'rate',
                label: t('drawer.rate'),
                icon: <Star />,
                onClick: () => {
                  setIsOpen(false);
                  if (STORE_URL) window.open(STORE_URL, '_blank', 'noopener');
                  else showToast(t('drawer.rateSoon'));
                },
              },
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
      <ThemePicker open={pickingTheme} onClose={() => setPickingTheme(false)} />
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
