import { BellRing } from 'lucide-react';
import type { PushStatus } from '../lib/push';
import { Button } from './Button';
import { Card } from './Card';
import { IconCircle } from './IconCircle';
import { useTranslation } from 'react-i18next';

export interface PushPromptCardProps {
  audience: 'rider' | 'partner';
  busy: boolean;
  onTurnOn: () => void;
  onDismiss: () => void;
}

// Copy lives in the ds translations under push.rider / push.partner.

/** The one place push permission is asked for - from her tap, never on page load. */
export function PushPromptCard({ audience, busy, onTurnOn, onDismiss }: PushPromptCardProps) {
  const { t } = useTranslation('ds');
  return (
    <Card tone="brand" className="space-y-3" data-testid="push-prompt">
      <div className="flex items-start gap-3">
        <IconCircle tone="soft" icon={<BellRing />} />
        <div className="min-w-0 flex-1">
          <p className="font-heading font-semibold text-text-primary">{t(`push.${audience}.title`)}</p>
          <p className="mt-0.5 text-sm text-text-secondary">{t(`push.${audience}.body`)}</p>
        </div>
      </div>
      <div className="flex gap-2">
        <Button size="md" variant="secondary" fullWidth disabled={busy} onClick={onDismiss}>
          {t('common.notNow')}
        </Button>
        <Button size="md" fullWidth disabled={busy} onClick={onTurnOn}>
          {busy ? t('push.turningOn') : t('push.turnOn')}
        </Button>
      </div>
    </Card>
  );
}

/** Where push stands on this device, for the Notifications screen - with a way to act where there is one. */
export function PushStatusNote({
  status,
  busy,
  onTurnOn,
}: {
  status: PushStatus;
  busy: boolean;
  onTurnOn: () => void;
}) {
  const { t } = useTranslation('ds');
  if (status === 'checking' || status === 'on' || status === 'unavailable') return null;
  const text =
    status === 'blocked'
      ? t('push.blocked')
      : status === 'unsupported'
        ? t('push.unsupported')
        : t('push.off');
  return (
    <Card className="flex items-center gap-3" data-testid="push-status">
      <p className="flex-1 text-sm text-text-secondary">{text}</p>
      {status === 'off' && (
        <Button size="md" disabled={busy} onClick={onTurnOn}>
          {busy ? '...' : t('push.turnOn')}
        </Button>
      )}
    </Card>
  );
}
