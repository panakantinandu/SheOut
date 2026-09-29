import { useGoBack } from '../lib/useGoBack';
import { LegalDocumentView, PRIVACY_POLICY } from '@sheout/design-system';

/** Reachable from the sign-in screen and from Profile, signed in or not. */
export function Privacy() {
  const goBack = useGoBack('/');
  return <LegalDocumentView document={PRIVACY_POLICY} onBack={goBack} />;
}
