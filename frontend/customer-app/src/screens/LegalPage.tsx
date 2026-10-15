import { useLocation } from 'react-router-dom';
import { hasAppHistory, useGoBack } from '../lib/useGoBack';
import { LEGAL_PAGES, LegalDocumentView, openedAsLegalTab, type LegalPath } from '@sheout/design-system';

/**
 * Every legal page (Privacy Policy, Terms, Partner Terms, Refunds, ...), each
 * at its own address and readable signed in or not. Opened from the app it is
 * a tab of its own (see openLegalDocument) and reads as a document, with a
 * Close button; reached directly - a shared link - it reads as a document
 * too, since there is no app behind it to go back to.
 */
export function LegalPage({ path }: { path: LegalPath }) {
  const goBack = useGoBack('/');
  const asTab = openedAsLegalTab(useLocation().search);
  return (
    <LegalDocumentView document={LEGAL_PAGES[path]} onBack={goBack} standalone={asTab || !hasAppHistory()} canClose={asTab} />
  );
}
