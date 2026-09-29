import type { SellerCategory } from '../api/types';

/**
 * What the seller registration wizard keeps on this phone between visits:
 * the step she was on, and anything typed that the server has not been
 * given yet (a category picked on Step 1 before her shop exists, or Step 2
 * edits not yet saved with Continue).
 * <p>
 * The server's shop is still the truth once it exists - this only carries
 * what would otherwise be lost to a refresh or a trip to the product editor
 * and back. Keyed by account, so a shared phone never shows one woman's
 * draft to another, and cleared once the shop is sent for review.
 * <p>
 * Every read and write is guarded: private windows and blocked storage throw,
 * and the wizard must still work there, only without the memory.
 */
export interface WizardDraft {
  /** 0 category, 1 business details, 2 products, 3 review. */
  step: number;
  category?: SellerCategory;
  businessName?: string;
  contactPhone?: string;
  whatsappNumber?: string;
  area?: string;
  /** True while the fields above differ from what the server holds. */
  dirty?: boolean;
}

const key = (accountId: string | null) => `sheout.sellerWizard.v1.${accountId ?? 'anon'}`;

export function readWizardDraft(accountId: string | null): WizardDraft | null {
  try {
    const raw = localStorage.getItem(key(accountId));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as WizardDraft;
    return typeof parsed?.step === 'number' ? parsed : null;
  } catch {
    return null;
  }
}

export function writeWizardDraft(accountId: string | null, draft: WizardDraft): void {
  try {
    localStorage.setItem(key(accountId), JSON.stringify(draft));
  } catch {
    // Storage unavailable: the wizard still works, it just will not remember.
  }
}

export function clearWizardDraft(accountId: string | null): void {
  try {
    localStorage.removeItem(key(accountId));
  } catch {
    // Nothing to clear.
  }
}
