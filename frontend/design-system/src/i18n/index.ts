import i18next, { type Resource } from 'i18next';
import { initReactI18next, useTranslation } from 'react-i18next';
import dsEn from './en.json';
import dsHi from './hi.json';
import dsTe from './te.json';

/**
 * Translation for both apps.
 * <p>
 * Three namespaces:
 * <ul>
 *   <li>translation - each app's own screens (its en/te/hi.json).</li>
 *   <li>ds - the copy inside shared components (this folder's json).</li>
 *   <li>safety - SOS, the pickup code, emergency contacts, anything a woman
 *       might read while frightened. Kept apart so it can be reviewed apart:
 *       see SafetyText.</li>
 * </ul>
 * English is the fallback for anything missing, so an untranslated string
 * shows in English rather than as a raw key.
 */

export const LANGUAGES = [
  // Native names, so someone who cannot read the current language can still
  // find her own.
  { code: 'en', nativeName: 'English', englishName: 'English' },
  { code: 'te', nativeName: 'తెలుగు', englishName: 'Telugu' },
  { code: 'hi', nativeName: 'हिन्दी', englishName: 'Hindi' },
] as const;

export type AppLanguage = (typeof LANGUAGES)[number]['code'];

const SUPPORTED: readonly string[] = LANGUAGES.map((l) => l.code);
const STORAGE_KEY = 'sheout_language';

export function isAppLanguage(code: unknown): code is AppLanguage {
  return typeof code === 'string' && SUPPORTED.includes(code);
}

export interface AppTranslations {
  translation: Record<AppLanguage, object>;
  safety: Record<AppLanguage, object>;
  /**
   * Which languages' safety copy a native speaker has checked. Until one
   * has, SafetyText shows the English beneath every safety string in that
   * language. Flip a language to true only after that review.
   */
  safetyReviewed: Record<AppLanguage, boolean>;
}

let safetyReviewed: Record<AppLanguage, boolean> = { en: true, te: false, hi: false };

/**
 * Her own earlier choice first; then the phone's language if it is one we
 * have; English otherwise. Only the language part is compared, so te-IN and
 * hi-IN both match.
 */
export function detectInitialLanguage(): AppLanguage {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (isAppLanguage(saved)) return saved;
  } catch {
    // Storage blocked - fall through to the browser.
  }
  const preferred = typeof navigator !== 'undefined' ? navigator.languages ?? [navigator.language] : [];
  for (const tag of preferred) {
    const base = tag?.toLowerCase().split('-')[0];
    if (isAppLanguage(base)) return base;
  }
  return 'en';
}

/** Call once, before the first render. */
export function initAppI18n(app: AppTranslations): typeof i18next {
  safetyReviewed = { ...app.safetyReviewed, en: true };
  const resources: Resource = {};
  for (const { code } of LANGUAGES) {
    resources[code] = {
      translation: app.translation[code] as Resource[string][string],
      safety: app.safety[code] as Resource[string][string],
      ds: ({ en: dsEn, te: dsTe, hi: dsHi } as Record<AppLanguage, object>)[code] as Resource[string][string],
    };
  }
  const lng = detectInitialLanguage();
  void i18next.use(initReactI18next).init({
    resources,
    lng,
    fallbackLng: 'en',
    ns: ['translation', 'safety', 'ds'],
    defaultNS: 'translation',
    interpolation: { escapeValue: false },
    returnNull: false,
  });
  applyDocumentLanguage(lng);
  return i18next;
}

function applyDocumentLanguage(lng: AppLanguage) {
  if (typeof document !== 'undefined') document.documentElement.lang = lng;
}

/** Set when she picks a language on this phone and it has not reached her account yet. */
const PENDING_SYNC_KEY = 'sheout_language_pending_sync';

function applyLanguage(lng: AppLanguage) {
  void i18next.changeLanguage(lng);
  applyDocumentLanguage(lng);
  try {
    localStorage.setItem(STORAGE_KEY, lng);
  } catch {
    // Storage blocked - the choice lasts for this session.
  }
}

/**
 * Her own choice, from the picker. Switches the language everywhere at once,
 * remembers it on this device - so Splash and Login, shown before anyone is
 * signed in, use it too - and marks it to be saved to her account.
 */
export function setAppLanguage(lng: AppLanguage): void {
  applyLanguage(lng);
  try {
    localStorage.setItem(PENDING_SYNC_KEY, '1');
  } catch {
    // Storage blocked - it will not be pushed to the account later.
  }
}

export interface LanguagePreferenceApi {
  get(): Promise<{ language: string | null }>;
  save(language: AppLanguage): Promise<unknown>;
}

/**
 * Keeps the app's language and the account's in step, once she is signed in.
 * <p>
 * A choice made on this phone and not yet saved wins: it is the most recent
 * thing she did, and it is pushed to the account. Otherwise the account's
 * language is applied - that is what makes it follow her to a new phone or a
 * fresh sign-in. An account with no language yet keeps whatever the phone is
 * showing, which came from the phone's own settings.
 */
export async function syncLanguageWithAccount(api: LanguagePreferenceApi): Promise<void> {
  let pending = false;
  try {
    pending = localStorage.getItem(PENDING_SYNC_KEY) === '1';
  } catch {
    // Storage blocked - treat as nothing pending.
  }
  if (pending) {
    await api.save(currentLanguage());
    try {
      localStorage.removeItem(PENDING_SYNC_KEY);
    } catch {
      // Nothing to do.
    }
    return;
  }
  const { language } = await api.get();
  if (isAppLanguage(language) && language !== currentLanguage()) applyLanguage(language);
}

/**
 * Her pick from the picker while signed in: applied at once, saved to the
 * account straight away. Resolves false if saving failed - the language has
 * still changed on this phone and will be pushed at the next sync.
 */
export async function chooseLanguage(lng: AppLanguage, api?: LanguagePreferenceApi): Promise<boolean> {
  setAppLanguage(lng);
  if (!api) return true;
  try {
    await api.save(lng);
    localStorage.removeItem(PENDING_SYNC_KEY);
    return true;
  } catch {
    return false;
  }
}

export function currentLanguage(): AppLanguage {
  const lng = i18next.resolvedLanguage ?? i18next.language;
  return isAppLanguage(lng) ? lng : 'en';
}

/** The current language, re-rendering when it changes. */
export function useAppLanguage(): AppLanguage {
  const { i18n } = useTranslation();
  const lng = i18n.resolvedLanguage ?? i18n.language;
  return isAppLanguage(lng) ? lng : 'en';
}

/** Whether a native speaker has signed off this language's safety copy. */
export function isSafetyReviewed(lng: AppLanguage): boolean {
  return safetyReviewed[lng] ?? false;
}

/** A shared-component string, callable outside React (the label helpers). */
export function dsT(key: string, fallback: string, values?: Record<string, unknown>): string {
  if (!i18next.isInitialized) return fallback;
  return i18next.t(key, { ns: 'ds', defaultValue: fallback, ...values }) as string;
}

export { useTranslation, Trans } from 'react-i18next';
export { i18next };
