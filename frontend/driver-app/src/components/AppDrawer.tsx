import { Gift, LifeBuoy, Settings } from 'lucide-react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Avatar, Drawer, useTranslation, vehicleLabel } from '@sheout/design-system';
import { usersApi } from '../api/client';
import type { DriverProfileSummary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { APP_VERSION } from '../lib/appVersion';


interface DrawerControls {
  open: () => void;
}

const DrawerContext = createContext<DrawerControls>({ open: () => undefined });

/** Opens the menu drawer from anywhere below AppDrawerProvider. */
export function useAppDrawer(): DrawerControls {
  return useContext(DrawerContext);
}

/**
 * The partner app's menu: the things she does that are not a tab - Refer
 * a Friend, then Help & support and Settings. The Profile tab is her work
 * (details, vehicle, verification, payouts), and nothing is in both: the
 * menu used to repeat Profile entry for entry. Plain names, no line under
 * each.
 * The header shows who is signed in and opens Profile, as a menu header does
 * in most apps. No Seller entry: that is a rider feature.
 */
export function AppDrawerProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);

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
              { key: 'refer', label: t('refer.title'), icon: <Gift />, onClick: () => go('/refer') },
            ],
          },
          {
            key: 'help',
            title: t('drawer.helpAndInfo'),
            items: [
              { key: 'help', label: t('profile.help'), icon: <LifeBuoy />, onClick: () => go('/help') },
              { key: 'settings', label: t('settings.title'), icon: <Settings />, onClick: () => go('/settings') },
            ],
          },
        ]}
        footer={t('drawer.version', { version: APP_VERSION })}
      />
    </DrawerContext.Provider>
  );
}
