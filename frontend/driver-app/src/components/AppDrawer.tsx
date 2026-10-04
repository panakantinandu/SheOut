import { Gift, Landmark, LifeBuoy, Settings, ShieldCheck, UserRound } from 'lucide-react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Avatar,
  Drawer,
  syncLanguageWithAccount,
  useTranslation,
  vehicleLabel, ASSISTANT_NAME, AssistantAvatar } from '@sheout/design-system';
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
 * The partner app's menu: shortcuts to the places she goes that are not a
 * tab - her profile, verification, payouts, help, Refer a Friend - and
 * Settings. Language, appearance, the legal pages and Log Out live in
 * Settings only; they used to be here as well as on Profile, so Log Out
 * appeared twice. No Seller entry: that is a rider feature.
 * <p>
 * Also keeps the language in step with her account once she is signed in -
 * see syncLanguageWithAccount.
 */
export function AppDrawerProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
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

  return (
    <DrawerContext.Provider value={controls}>
      {children}
      <Drawer
        open={isOpen}
        onClose={close}
        header={
          <button type="button" onClick={() => go('/profile')} className="flex w-full items-center gap-3 text-left">
            <Avatar url={profile?.profilePhotoUrl} name={profile?.name} size="lg" />
            {/* Blank until her profile has loaded, rather than "Add your name" flashing before her name. */}
            <div className="min-w-0">
              <p className="truncate font-heading text-section">{profile ? profile.name || t('profile.addName') : ''}</p>
              <p className="truncate text-sm opacity-85">
                {profile ? `${t('drawer.partner')} · ${vehicleLabel(profile.vehicleType)}` : t('drawer.partner')}
              </p>
            </div>
          </button>
        }
        sections={[
          {
            key: 'main',
            items: [
              { key: 'profile', label: t('profile.title'), icon: <UserRound />, onClick: () => go('/profile') },
              { key: 'verification', label: t('verification.title'), icon: <ShieldCheck />, onClick: () => go('/verification') },
              { key: 'payouts', label: t('drawer.payouts'), icon: <Landmark />, onClick: () => go('/payouts') },
              { key: 'refer', label: t('refer.title'), icon: <Gift />, onClick: () => go('/refer') },
            ],
          },
          {
            key: 'help',
            title: t('drawer.helpAndInfo'),
            items: [
              { key: 'assistant', label: t('help.askAssistant', { name: ASSISTANT_NAME }), icon: <AssistantAvatar size={22} staticOnly />, onClick: () => go('/help/assistant') },
              { key: 'support', label: t('drawer.support'), icon: <LifeBuoy />, onClick: () => go('/help') },
              { key: 'settings', label: t('settings.title'), icon: <Settings />, onClick: () => go('/settings') },
            ],
          },
        ]}
        footer={t('drawer.version', { version: APP_VERSION })}
      />
    </DrawerContext.Provider>
  );
}
