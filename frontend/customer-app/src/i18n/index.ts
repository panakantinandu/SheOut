import { initAppI18n } from '@sheout/design-system';
import en from './en.json';
import hi from './hi.json';
import te from './te.json';
import enSafety from './en.safety.json';
import hiSafety from './hi.safety.json';
import teSafety from './te.safety.json';

/**
 * The rider app's translations. Imported once, from main.tsx, before the
 * first render.
 * <p>
 * safetyReviewed: the SOS, pickup-code and emergency-contact copy in
 * *.safety.json was machine-translated and has NOT been checked by a native
 * speaker. Until it has, every safety string in that language is shown with
 * the English beneath it - see SafetyText and SAFETY_TRANSLATION_REVIEW.md.
 * Set a language to true only after that review.
 */
initAppI18n({
  translation: { en, te, hi },
  safety: { en: enSafety, te: teSafety, hi: hiSafety },
  safetyReviewed: { en: true, te: false, hi: false },
});
