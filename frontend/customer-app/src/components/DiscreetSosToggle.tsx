import { Vibrate } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Button, SafetyText } from '@sheout/design-system';
import { discreetSosEnabled, motionSupported, requestMotionPermission, setDiscreetSosEnabled } from '../lib/discreetSos';

/**
 * Turning discreet SOS on or off. Turning it on is a tap, and that tap is
 * what asks an iPhone for motion-sensor permission - here, calmly, during the
 * introduction or in the Safety Center, never as a surprise mid-emergency.
 */
export function DiscreetSosToggle({ compact = false }: { compact?: boolean }) {
  const [on, setOn] = useState(discreetSosEnabled);
  const [denied, setDenied] = useState(false);
  const supported = motionSupported();

  useEffect(() => {
    const update = () => setOn(discreetSosEnabled());
    window.addEventListener('sheout-discreet-sos', update);
    return () => window.removeEventListener('sheout-discreet-sos', update);
  }, []);

  if (!supported) {
    return (
      <p className="text-sm text-text-secondary" data-testid="discreet-unsupported">
        <SafetyText k="discreet.unsupported" />
      </p>
    );
  }

  async function turnOn() {
    const permission = await requestMotionPermission();
    if (permission === 'granted') {
      setDiscreetSosEnabled(true);
      setOn(true);
      setDenied(false);
    } else {
      setDenied(true);
    }
  }

  return (
    <div className={compact ? 'space-y-2' : 'space-y-3'} data-testid="discreet-toggle" data-on={on}>
      {on ? (
        <>
          <p className="flex items-center justify-center gap-2 text-sm font-semibold text-success">
            <Vibrate className="h-4 w-4" aria-hidden="true" />
            <SafetyText k="discreet.isOn" />
          </p>
          <Button
            fullWidth
            size="md"
            variant="secondary"
            onClick={() => {
              setDiscreetSosEnabled(false);
              setOn(false);
            }}
            data-testid="discreet-turn-off"
          >
            <SafetyText k="discreet.turnOff" englishClassName="font-normal" />
          </Button>
        </>
      ) : (
        <Button fullWidth size="md" icon={<Vibrate className="h-4 w-4" />} onClick={turnOn} data-testid="discreet-turn-on">
          <SafetyText k="discreet.turnOn" englishClassName="font-normal" />
        </Button>
      )}
      {denied && (
        <p className="text-sm text-danger" role="alert">
          <SafetyText k="discreet.denied" />
        </p>
      )}
    </div>
  );
}
