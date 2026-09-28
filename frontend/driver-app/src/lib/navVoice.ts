import { useCallback, useEffect, useRef, useState } from 'react';

const VOICE_KEY = 'sheout_nav_voice';

/**
 * Spoken turn instructions, through the phone's own text-to-speech (the Web
 * Speech API: free, offline once the voice is installed, no vendor).
 * <p>
 * In her language when the phone has a voice for it: Hindi voices ship
 * with most Android phones, Telugu with fewer. Without one it speaks
 * English, from the English sentence, rather than reading Telugu text with
 * an English voice, which is unintelligible.
 * <p>
 * On by default, the way ride apps ship it: a partner on a bike cannot read
 * a banner at speed. Muting is remembered on this phone.
 */
export function useNavVoice(language: string, englishFor: (key: string, values?: Record<string, unknown>) => string) {
  const supported = typeof window !== 'undefined' && 'speechSynthesis' in window;
  const [enabled, setEnabled] = useState(() => {
    try {
      return localStorage.getItem(VOICE_KEY) !== 'off';
    } catch {
      return true;
    }
  });
  const englishRef = useRef(englishFor);
  englishRef.current = englishFor;

  const toggle = useCallback(() => {
    setEnabled((on) => {
      const next = !on;
      try {
        localStorage.setItem(VOICE_KEY, next ? 'on' : 'off');
      } catch {
        // A private window: the choice lasts for this session only.
      }
      if (!next && supported) window.speechSynthesis.cancel();
      return next;
    });
  }, [supported]);

  /** Says the sentence under `key`: in her language if a voice exists for it, else in English. */
  const say = useCallback(
    (text: string, key: string, values?: Record<string, unknown>) => {
      if (!supported || !enabled) return;
      const synth = window.speechSynthesis;
      const wanted = language === 'hi' ? 'hi' : language === 'te' ? 'te' : 'en';
      const voices = synth.getVoices();
      const native = voices.find((v) => v.lang.toLowerCase().startsWith(wanted));
      const english = voices.find((v) => v.lang.toLowerCase() === 'en-in') ?? voices.find((v) => v.lang.toLowerCase().startsWith('en'));
      const useNative = wanted === 'en' || Boolean(native);
      const utterance = new SpeechSynthesisUtterance(useNative ? text : englishRef.current(key, values));
      utterance.lang = useNative ? (wanted === 'en' ? 'en-IN' : `${wanted}-IN`) : 'en-IN';
      const voice = useNative ? native ?? english : english;
      if (voice) utterance.voice = voice;
      utterance.rate = 1;
      // The newest instruction replaces one still being read: a stale "turn
      // left" finishing after she has turned is worse than a cut-off one.
      synth.cancel();
      synth.speak(utterance);
    },
    [supported, enabled, language]
  );

  useEffect(() => () => {
    if (supported) window.speechSynthesis.cancel();
  }, [supported]);

  return { supported, enabled, toggle, say };
}

/**
 * Keeps the screen on while she navigates (the Screen Wake Lock API). The
 * lock drops whenever the page is hidden, so it is taken again on return.
 * Browsers without it simply sleep as usual.
 */
export function useWakeLock(active: boolean) {
  useEffect(() => {
    if (!active || typeof navigator === 'undefined' || !('wakeLock' in navigator)) return;
    let lock: { release: () => Promise<void> } | null = null;
    let cancelled = false;
    const take = () => {
      (navigator as Navigator & { wakeLock: { request: (t: 'screen') => Promise<{ release: () => Promise<void> }> } }).wakeLock
        .request('screen')
        .then((l) => {
          if (cancelled) void l.release();
          else lock = l;
        })
        .catch(() => undefined);
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible') take();
    };
    take();
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      cancelled = true;
      document.removeEventListener('visibilitychange', onVisible);
      void lock?.release().catch(() => undefined);
    };
  }, [active]);
}
