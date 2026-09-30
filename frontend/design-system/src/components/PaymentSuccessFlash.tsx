import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';
import { Overlay } from './Overlay';
import { SuccessCheck } from './SuccessCheck';

export interface PaymentSuccessFlashProps {
  open: boolean;
  /** Rupees, shown large - the figure she is checking. */
  amount: number | null;
  /** "Payment successful" / "Payment received". */
  title: string;
  /** One line under it: who it went to, or where it landed. */
  message: string;
  /** Called once, on the button, a tap outside, or when it closes itself. */
  onDone: () => void;
  /** How long it stays before closing itself. */
  autoCloseMs?: number;
}

const SPARKS = Array.from({ length: 14 }, (_, i) => ({
  left: `${(i * 41 + 11) % 100}%`,
  drift: `${((i * 59) % 80) - 40}px`,
  delay: `${(i % 5) * 110}ms`,
  colour: ['bg-accent-green', 'bg-primary', 'bg-accent-orange', 'bg-primary-mid'][i % 4],
  shape: i % 3 === 0 ? 'h-2 w-2 rounded-full' : 'h-2.5 w-1.5 rounded-sm',
}));

/**
 * The moment the fare lands: "it worked", said so nobody has to wonder.
 * <p>
 * Every ride app does this, for a reason - paying is the one step where a
 * rider is left asking "did the money go?" and a partner "did I get paid?",
 * and a card quietly changing colour answers neither. So: a ring that
 * ripples out, the check drawing itself, the amount, one line of who it
 * went to, and a few sparks. Then it closes itself.
 * <p>
 * SHOWN ONCE, AT THE CAPTURE. The caller opens it only on the change from
 * unpaid to paid while the screen is open - never for a trip opened from
 * history, which is why SuccessCheck's argument against celebrating the end
 * of a trip does not reach this: it is about the payment, not the journey.
 * <p>
 * A short buzz on phones that have one. Under reduced motion there is no
 * ripple and no sparks; the check is simply there.
 */
export function PaymentSuccessFlash({ open, amount, title, message, onDone, autoCloseMs = 3600 }: PaymentSuccessFlashProps) {
  const { t } = useTranslation('ds');
  const done = useRef(onDone);
  done.current = onDone;

  useEffect(() => {
    if (!open) return;
    try {
      navigator.vibrate?.([40, 60, 40]);
    } catch {
      // Not every browser allows it; the screen says it anyway.
    }
    const timer = window.setTimeout(() => done.current(), autoCloseMs);
    return () => window.clearTimeout(timer);
  }, [open, autoCloseMs]);

  return (
    <Overlay open={open} label={title} onDismiss={onDone} className="px-6">
      <div
        className="relative w-full max-w-sm overflow-hidden rounded-card bg-surface px-6 pb-6 pt-9 text-center shadow-xl motion-safe:animate-pop-in"
        role="status"
        aria-live="assertive"
        data-testid="payment-success-flash"
      >
        <div className="pointer-events-none absolute inset-x-0 top-0 h-full motion-reduce:hidden" aria-hidden="true">
          {SPARKS.map((s, i) => (
            <span
              key={i}
              className={`absolute top-0 ${s.shape} ${s.colour} animate-confetti-fall`}
              style={{ left: s.left, animationDelay: s.delay, ['--confetti-drift' as string]: s.drift }}
            />
          ))}
        </div>
        <div className="relative">
          <div className="relative mx-auto flex h-28 w-28 items-center justify-center">
            <span className="absolute inset-0 rounded-full bg-accent-green/20 motion-safe:animate-ripple" aria-hidden="true" />
            <span
              className="absolute inset-0 rounded-full bg-accent-green/15 motion-safe:animate-ripple"
              style={{ animationDelay: '0.8s' }}
              aria-hidden="true"
            />
            <span className="relative flex h-24 w-24 items-center justify-center rounded-full bg-surface motion-safe:animate-nav-pop">
              <SuccessCheck size={96} label={title} />
            </span>
          </div>
          <h2 className="mt-5 font-heading text-title text-text-primary">{title}</h2>
          {amount != null && (
            <p className="mt-2 font-heading text-4xl font-bold text-accent-green-strong motion-safe:animate-fade-slide-in" data-testid="payment-success-amount">
              ₹{Number.isInteger(amount) ? amount : amount.toFixed(2)}
            </p>
          )}
          <p className="mt-2 text-text-secondary">{message}</p>
          <Button className="mt-6 w-full" size="lg" variant="success" onClick={onDone} data-testid="payment-success-continue">
            {t('paymentFlash.continue')}
          </Button>
          {/* How long before it closes by itself - so nobody taps in a hurry. */}
          <div className="mt-3 h-1 overflow-hidden rounded-full bg-background" aria-hidden="true">
            <div
              className="h-full origin-left rounded-full bg-accent-green/60 motion-safe:animate-fill-x"
              style={{ animationDuration: `${autoCloseMs}ms` }}
            />
          </div>
        </div>
      </div>
    </Overlay>
  );
}
