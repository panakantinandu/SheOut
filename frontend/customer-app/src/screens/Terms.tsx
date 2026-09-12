import { useNavigate } from 'react-router-dom';
import { LegalDocumentView, TERMS_OF_SERVICE } from '@sheout/design-system';

/** Reachable from the sign-in screen and from Profile, signed in or not. */
export function Terms() {
  const navigate = useNavigate();
  return <LegalDocumentView document={TERMS_OF_SERVICE} onBack={() => navigate(-1)} />;
}
