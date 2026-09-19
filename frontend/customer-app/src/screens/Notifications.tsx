import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  NotificationInbox,
  PushStatusNote,
  TopHeader,
  usePushMessages,
  usePushNotifications,
} from '@sheout/design-system';
import { PUSH_TOKEN_KEY, notificationsApi, pushApi } from '../api/client';
import { useTranslation } from '@sheout/design-system';

/**
 * The inbox: every notification SheOut sent this account, newest first,
 * whether or not a copy reached the phone. What it said and where it leads -
 * never how it was delivered. The old screen listed SMS delivery attempts,
 * so on a trial SMS account every row read "Failed".
 */
export function Notifications() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const push = usePushNotifications(pushApi, PUSH_TOKEN_KEY, true);
  const [refreshKey, setRefreshKey] = useState(0);
  usePushMessages(useCallback(() => setRefreshKey((k) => k + 1), []));

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('notifications.title')} onBack={() => navigate('/home')} />
      <PushStatusNote status={push.status} busy={push.busy} onTurnOn={push.turnOn} />
      <NotificationInbox
        fetchPage={notificationsApi.inbox}
        markRead={notificationsApi.markRead}
        markAllRead={notificationsApi.markAllRead}
        onOpen={(link) => navigate(link)}
        refreshKey={refreshKey}
      />
    </div>
  );
}
