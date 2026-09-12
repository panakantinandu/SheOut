import { useNavigate } from 'react-router-dom';
import { LegalDocumentView, PRIVACY_POLICY } from '@sheout/design-system';

/** Reachable from the sign-in screen and from Profile, signed in or not. */
export function Privacy() {
  const navigate = useNavigate();
  return <LegalDocumentView document={PRIVACY_POLICY} onBack={() => navigate(-1)} />;
}
