import { Check } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';

export interface StepperProps {
  /** One name per step, in order. Read out for each dot; the count is the number of dots. */
  steps: string[];
  /** The step on screen, from 0. */
  current: number;
  /**
   * Jump back to a finished step. Only steps before the current one are
   * offered: a dot for a step she has not reached yet would skip whatever
   * that step checks.
   */
  onStepClick?: (index: number) => void;
  className?: string;
}

/**
 * Numbered progress through a short flow: a row of dots joined by a line.
 * <p>
 * Finished steps are filled in the brand colour with a tick, the current
 * step is filled with its number and a soft halo, and the steps still to
 * come are outlined and muted. The line between two dots fills once the
 * first of them is done, so the row reads as distance covered, not only as
 * a count.
 * <p>
 * A list, not a progress bar: each dot is announced with its name and
 * whether it is done, and the current one carries aria-current="step".
 */
export function Stepper({ steps, current, onStepClick, className }: StepperProps) {
  const { t } = useTranslation('ds');
  return (
    <nav aria-label={t('stepper.label', { current: current + 1, total: steps.length })} className={className}>
      <ol className="flex items-center">
        {steps.map((name, i) => {
          const done = i < current;
          const active = i === current;
          const canJump = done && !!onStepClick;
          const dot = (
            <span
              className={cn(
                'flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-caption font-bold transition-colors duration-200',
                done && 'bg-primary text-text-inverse',
                active && 'bg-primary text-text-inverse ring-4 ring-primary-light',
                !done && !active && 'border-2 border-border bg-surface text-text-secondary'
              )}
            >
              {done ? <Check className="h-4 w-4" strokeWidth={3} aria-hidden="true" /> : i + 1}
            </span>
          );
          const status = done ? t('stepper.done') : active ? t('stepper.current') : t('stepper.upcoming');
          return (
            <li
              key={name}
              className={cn('flex items-center', i < steps.length - 1 && 'flex-1')}
              aria-current={active ? 'step' : undefined}
              data-testid={`step-${i + 1}`}
              data-state={done ? 'done' : active ? 'current' : 'upcoming'}
            >
              {canJump ? (
                <button
                  type="button"
                  onClick={() => onStepClick(i)}
                  className="rounded-full focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  aria-label={`${name}, ${status}`}
                >
                  {dot}
                </button>
              ) : (
                <span aria-label={`${name}, ${status}`} role="img">
                  {dot}
                </span>
              )}
              {i < steps.length - 1 && (
                <span className="mx-1.5 h-0.5 flex-1 overflow-hidden rounded-full bg-border" aria-hidden="true">
                  <span
                    className={cn(
                      'block h-full rounded-full bg-primary transition-[width] duration-300 ease-out motion-reduce:transition-none',
                      done ? 'w-full' : 'w-0'
                    )}
                  />
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
