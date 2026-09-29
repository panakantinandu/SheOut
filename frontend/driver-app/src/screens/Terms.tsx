import { useGoBack } from '../lib/useGoBack';
import { LegalDocumentView, TERMS_OF_SERVICE } from '@sheout/design-system';

/** Reachable from the sign-in screen and from Profile, signed in or not. */
export function Terms() {
  const goBack = useGoBack('/');
  return <LegalDocumentView document={TERMS_OF_SERVICE} onBack={goBack} />;
}
