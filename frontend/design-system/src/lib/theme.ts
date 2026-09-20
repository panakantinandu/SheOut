import { useEffect, useState } from 'react';

/** What she chose, not what she is seeing: "system" follows the phone. */
export type ThemeChoice = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'sheout_theme';

/**
 * Applied to <html> so the CSS variables in theme.css switch over.
 * <p>
 * "system" removes the attribute rather than writing a resolved value, so a
 * phone that switches to dark at sunset takes the app with it without her
 * having to open the app for it to notice.
 */
export function applyTheme(choice: ThemeChoice): void {
  const root = document.documentElement;
  if (choice === 'system') {
    root.removeAttribute('data-theme');
  } else {
    root.setAttribute('data-theme', choice);
  }
  // The browser's own chrome - form controls, scrollbars, the address bar
  // tint - follows this, so it is set alongside rather than left light.
  root.style.colorScheme = choice === 'system' ? '' : choice;
}

export function storedTheme(): ThemeChoice {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
  } catch {
    // Private mode, or storage blocked. The device's setting is a fine
    // answer, and it is the one somebody who has never chosen would get.
    return 'system';
  }
}

export function storeTheme(choice: ThemeChoice): void {
  try {
    localStorage.setItem(STORAGE_KEY, choice);
  } catch {
    // Nothing to do: the theme still applies for this visit.
  }
}

/** Which of the two she is actually looking at right now. */
export function resolvedTheme(choice: ThemeChoice): 'light' | 'dark' {
  if (choice !== 'system') return choice;
  return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

/**
 * The theme, as state.
 * <p>
 * The stored choice is applied before first paint by a small script in
 * index.html - see THEME_BOOTSTRAP - so this hook never causes the white
 * flash that gives away a dark-mode app doing it in JavaScript.
 */
export function useTheme(): [ThemeChoice, (choice: ThemeChoice) => void, 'light' | 'dark'] {
  const [choice, setChoice] = useState<ThemeChoice>(() => storedTheme());
  const [resolved, setResolved] = useState<'light' | 'dark'>(() => resolvedTheme(storedTheme()));

  useEffect(() => {
    applyTheme(choice);
    setResolved(resolvedTheme(choice));
    if (choice !== 'system' || typeof matchMedia !== 'function') return;
    const media = matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => setResolved(media.matches ? 'dark' : 'light');
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, [choice]);

  return [
    choice,
    (next: ThemeChoice) => {
      storeTheme(next);
      setChoice(next);
    },
    resolved,
  ];
}

/**
 * The same logic, small enough to inline in <head>.
 * <p>
 * It runs before the first paint, so the app opens in the theme she chose
 * rather than flashing white and then correcting itself. Kept here beside
 * the real implementation so the two cannot drift.
 */
export const THEME_BOOTSTRAP = `(function(){try{var t=localStorage.getItem('sheout_theme');if(t==='dark'||t==='light'){document.documentElement.setAttribute('data-theme',t);document.documentElement.style.colorScheme=t;}}catch(e){}})();`;
