import { BellRing } from 'lucide-react';
import type { PushStatus } from '../lib/push';
import { Button } from './Button';
import { Card } from './Card';
import { IconCircle } from './IconCircle';

export interface PushPromptCardProps {
  audience: 'rider' | 'partner';
  busy: boolean;
  onTurnOn: () => void;
  onDismiss: () => void;
}

const COPY = {
  rider: {
    title: 'Turn on notifications',
    body: 'Know the moment your partner accepts, when she is arriving, and when support replies - even with SheOut closed.',
  },
  partner: {
    title: 'Turn on trip alerts',
    body: 'New trip requests only last 15 seconds. With alerts on, your phone rings and vibrates for each one, even when SheOut is closed.',
  },
} as const;

/** The one place push permission is asked for - from her tap, never on page load. */
export function PushPromptCard({ audience, busy, onTurnOn, onDismiss }: PushPromptCardProps) {
  const copy = COPY[audience];
  return (
    <Card tone="brand" className="space-y-3" data-testid="push-prompt">
      <div className="flex items-start gap-3">
        <IconCircle tone="soft" icon={<BellRing />} />
        <div className="min-w-0 flex-1">
          <p className="font-heading font-semibold text-text-primary">{copy.title}</p>
          <p className="mt-0.5 text-sm text-text-secondary">{copy.body}</p>
        </div>
      </div>
      <div className="flex gap-2">
        <Button size="md" variant="secondary" fullWidth disabled={busy} onClick={onDismiss}>
          Not now
        </Button>
        <Button size="md" fullWidth disabled={busy} onClick={onTurnOn}>
          {busy ? 'Turning on...' : 'Turn on'}
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
  if (status === 'checking' || status === 'on' || status === 'unavailable') return null;
  const text =
    status === 'blocked'
      ? 'Notifications are blocked for SheOut in this browser. Allow them in the site settings to get alerts on this device.'
      : status === 'unsupported'
        ? 'This browser cannot show SheOut notifications. On an iPhone, add SheOut to your Home Screen first.'
        : 'Notifications are off on this device. Everything still appears here.';
  return (
    <Card className="flex items-center gap-3" data-testid="push-status">
      <p className="flex-1 text-sm text-text-secondary">{text}</p>
      {status === 'off' && (
        <Button size="md" disabled={busy} onClick={onTurnOn}>
          {busy ? '...' : 'Turn on'}
        </Button>
      )}
    </Card>
  );
}
