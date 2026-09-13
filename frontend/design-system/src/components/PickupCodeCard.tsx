import { KeyRound } from 'lucide-react';
import { Card } from './Card';
import { IconCircle } from './IconCircle';

export interface PickupCodeCardProps {
  /** Null while it is still being fetched, or when the booking has no code. */
  code: string | null;
  className?: string;
}

/**
 * The four digits a rider reads out to her partner before getting in.
 * <p>
 * Deliberately the loudest thing on the tracking screen once a partner is
 * on her way, because it is the one piece of the screen she has to act on.
 * Everything else there - the map, the fare, the vehicle - she reads. This
 * she says out loud, and the trip does not start until she does.
 * <p>
 * The instruction says "read it out", not "show your screen". Handing over
 * an unlocked phone at a kerb at night is a worse habit than speaking four
 * digits, and the wording is what teaches the habit.
 * <p>
 * Wide letter-spacing and a large size because it is read aloud from arm's
 * length, in the dark, through a car window.
 */
export function PickupCodeCard({ code, className }: PickupCodeCardProps) {
  if (!code) return null;

  return (
    <Card tone="brand" className={className}>
      <div className="flex items-center gap-3">
        <IconCircle tone="soft" icon={<KeyRound />} />
        <div className="min-w-0 flex-1">
          <p className="font-heading font-semibold text-text-primary">Your pickup code</p>
          <p className="mt-0.5 text-sm text-text-secondary">
            Read this out to your partner before you get in. She cannot start the trip without it.
          </p>
        </div>
      </div>
      <p
        // Spaced so the digits are read as four separate numbers rather than
        // as one four-digit number, which is how somebody says them aloud.
        className="mt-3 text-center font-heading text-4xl font-bold tracking-[0.35em] text-primary"
        aria-label={`Your pickup code is ${code.split('').join(' ')}`}
      >
        {code}
      </p>
    </Card>
  );
}
