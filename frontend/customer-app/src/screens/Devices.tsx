import { LogOut, Smartphone } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  ConfirmDialog,
  IconCircle,
  ListEmptyState,
  PullToRefresh,
  SkeletonList,
  TopHeader,
  useTranslation,
} from '@sheout/design-system';
import { sessionsApi } from '../api/client';
import type { AccountSession } from '../api/types';
import { apiErrorText } from '../lib/apiErrors';

/**
 * Where she is signed in, and how to end any of it.
 * <p>
 * The point is recognising a device she does not know - a phone she lent to
 * somebody, a browser at a shop that stayed signed in - and cutting it off
 * without having to change anything else. Each row is a place, not a browser
 * string: "Android phone, last used 10 minutes ago" is something she can
 * match against what is in her hand.
 */
export function Devices() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<AccountSession[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ending, setEnding] = useState<AccountSession | null>(null);
  const [, setBusy] = useState(false);

  const load = useCallback(
    () =>
      sessionsApi
        .list()
        .then((list) => {
          setSessions(list);
          setError(null);
        })
        .catch((err) => setError(apiErrorText(err, 'devices.loadError'))),
    []
  );

  useEffect(() => {
    void load();
  }, [load]);

  async function endSession(session: AccountSession) {
    setBusy(true);
    try {
      await sessionsApi.end(session.id);
      // Signing out the device she is holding is signing out: the next
      // request is refused and the app takes her to the sign-in screen.
      if (!session.current) await load();
      setEnding(null);
    } catch (err) {
      setError(apiErrorText(err, 'devices.endError'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <PullToRefresh onRefresh={load} className="space-y-6">
      <TopHeader variant="back" title={t('devices.title')} onBack={() => navigate('/profile')} />
      <p className="text-sm text-text-secondary">{t('devices.intro')}</p>

      {error && <p className="text-sm text-danger">{error}</p>}
      {!sessions && !error && <SkeletonList rows={2} label={t('devices.loading')} />}

      {sessions && sessions.length === 0 && (
        <ListEmptyState illustrated icon={<Smartphone />} title={t('devices.emptyTitle')} message={t('devices.emptyMessage')} />
      )}

      {sessions && sessions.length > 0 && (
        <Card className="divide-y divide-border p-0" data-testid="device-list">
          {sessions.map((session) => (
            <div key={session.id} className="flex items-center gap-3 p-4" data-testid="device-row">
              <IconCircle tone="soft" size="sm" icon={<Smartphone />} />
              {/* The name gets the width it needs; "this device" and when it
                  was last used sit under it, so nothing is cut off on a
                  narrow phone. */}
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-text-primary">{session.device}</p>
                <p className="truncate text-xs text-text-secondary">
                  {/* When the device in her hand was last used is now, which
                      tells her nothing - it just crowds the row. */}
                  {session.current
                    ? <span className="font-semibold text-primary">{t('devices.thisDevice')}</span>
                    : t('devices.lastActive', { when: new Date(session.lastActiveAt).toLocaleString([], { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) })}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setEnding(session)}
                className="flex shrink-0 items-center gap-1 rounded-input px-2 py-1.5 text-sm font-semibold text-primary"
              >
                <LogOut className="h-4 w-4" />
                {t('devices.signOut')}
              </button>
            </div>
          ))}
        </Card>
      )}

      <ConfirmDialog
        open={ending !== null}
        title={ending?.current ? t('devices.confirmThisTitle') : t('devices.confirmTitle')}
        message={ending?.current ? t('devices.confirmThisMessage') : t('devices.confirmMessage', { device: ending?.device ?? '' })}
        confirmLabel={t('devices.signOut')}
        destructive
        onConfirm={() => ending && endSession(ending)}
        onCancel={() => setEnding(null)}
      />
    </PullToRefresh>
  );
}
