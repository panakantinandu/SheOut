import { useTranslation } from 'react-i18next';
import { Card } from './Card';
import { Button } from './Button';
import { SheOutWordmark } from './SheOutLogo';
import { LegalFooter } from './LegalFooter';

export interface DeleteAccountInfoProps {
  audience: 'customer' | 'driver';
  signedIn: boolean;
  /** Signed in: open the delete step in Settings. Signed out: sign in first. */
  onContinue: () => void;
}

/**
 * The public "Delete your account" page - an address anyone can open,
 * without the app, as app stores require. It says what deleting does and
 * what is kept, in the same words as the in-app step (privacy.explain.*),
 * and then asks her to sign in with her number: the one-time code is the
 * identity check, so nobody can delete an account that is not theirs.
 * The deletion itself is the in-app step (Settings, Privacy & Data).
 */
export function DeleteAccountInfo({ audience, signedIn, onContinue }: DeleteAccountInfoProps) {
  const { t } = useTranslation('ds');
  const lines = [
    t('privacy.explain.closed'),
    audience === 'driver' ? t('privacy.explain.removedDriver') : t('privacy.explain.removedRider'),
    t('privacy.explain.messages'),
    t('privacy.explain.kept'),
  ];
  return (
    <div className="space-y-6">
      <header className="space-y-4">
        <SheOutWordmark className="w-28" />
        <h1 className="font-heading text-title text-text-primary">{t('legal.pages.deleteAccount')}</h1>
      </header>
      <Card className="space-y-3">
        <p className="text-sm leading-relaxed text-text-primary">{t('deleteAccountPage.intro')}</p>
        <ul className="space-y-2">
          {lines.map((line) => (
            <li key={line} className="flex gap-2 text-sm leading-relaxed text-text-secondary">
              <span aria-hidden="true" className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
              <span>{line}</span>
            </li>
          ))}
        </ul>
      </Card>
      <Button fullWidth onClick={onContinue} data-testid="delete-account-continue">
        {signedIn ? t('deleteAccountPage.continue') : t('deleteAccountPage.signIn')}
      </Button>
      <p className="text-center text-xs text-text-secondary">{t('deleteAccountPage.help')}</p>
      <LegalFooter />
    </div>
  );
}
