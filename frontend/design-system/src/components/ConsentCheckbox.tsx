import { Check } from 'lucide-react';
import { Trans, useTranslation } from 'react-i18next';
import { cn } from '../lib/cn';

export interface ConsentCheckboxProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  onOpenTerms: () => void;
  onOpenPrivacy: () => void;
  className?: string;
}

/**
 * Agreeing to the Terms and the Privacy Policy, as something she does rather
 * than something she is told she has done.
 * <p>
 * The app used to say "by tapping Send OTP you agree" - consent implied by
 * using the thing she was trying to use, with nothing recorded anywhere. This
 * is an unticked box she has to tick, and ticking it is written down against
 * her account with the date and the version she saw (see AuthApi.acceptTerms).
 * <p>
 * It sits at sign-up rather than at every sign-in. The app cannot tell a new
 * number from a returning one before the code is verified - deliberately, so
 * the screen cannot be used to discover who has an account - so this asks at
 * the one moment it is certain: finishing a new account.
 * <p>
 * The whole row is the target, not the 20px box: this is the last thing
 * between her and the app, and a miss here reads as the app ignoring her.
 */
export function ConsentCheckbox({ checked, onChange, onOpenTerms, onOpenPrivacy, className }: ConsentCheckboxProps) {
  const { t } = useTranslation('ds');
  return (
    <label
      className={cn(
        'flex cursor-pointer items-start gap-3 rounded-card border p-3 transition-colors',
        checked ? 'border-primary bg-primary-light/50' : 'border-border bg-surface',
        className
      )}
      data-testid="consent-row"
    >
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="sr-only"
        data-testid="consent-checkbox"
      />
      <span
        aria-hidden="true"
        className={cn(
          'mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-[6px] border-2 transition-colors',
          checked ? 'border-primary bg-primary text-text-inverse' : 'border-border bg-surface'
        )}
      >
        {checked && <Check className="h-3.5 w-3.5" strokeWidth={3} />}
      </span>
      <span className="text-xs leading-relaxed text-text-secondary">
        <Trans
          ns="ds"
          i18nKey="consent.checkbox"
          components={{
            // Buttons, not links: they open the documents in the app, and a
            // tap on either must not also tick the box.
            terms: (
              <button
                type="button"
                onClick={(e) => {
                  e.preventDefault();
                  onOpenTerms();
                }}
                className="font-semibold text-primary underline"
              />
            ),
            privacy: (
              <button
                type="button"
                onClick={(e) => {
                  e.preventDefault();
                  onOpenPrivacy();
                }}
                className="font-semibold text-primary underline"
              />
            ),
          }}
        />
      </span>
      <span className="sr-only">{t('consent.checkboxAria')}</span>
    </label>
  );
}
