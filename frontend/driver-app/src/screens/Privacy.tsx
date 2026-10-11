import { useLocation } from 'react-router-dom';
import { hasAppHistory, useGoBack } from '../lib/useGoBack';
import { LegalDocumentView, PRIVACY_POLICY, openedAsLegalTab } from '@sheout/design-system';

/**
 * Its own address, readable signed in or not. Opened from the app it is a
 * tab of its own (see openLegalDocument) and reads as a document, with a
 * Close button; reached directly - a shared link - it reads as a document
 * too, since there is no app behind it to go back to.
 */
export function Privacy() {
  const goBack = useGoBack('/');
  const asTab = openedAsLegalTab(useLocation().search);
  return <LegalDocumentView document={PRIVACY_POLICY} onBack={goBack} standalone={asTab || !hasAppHistory()} canClose={asTab} />;
}
