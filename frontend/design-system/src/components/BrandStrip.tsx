import { SheOutWordmark } from './SheOutLogo';

/**
 * SheOut, by name, at the top of Home in both apps: the wordmark from the
 * logo sheet, and a short line under it, with a pulsing dot, saying which
 * app this is ("Partner") or what SheOut is ("By women · For women").
 * <p>
 * The wordmark alone. Its S is already the woman's profile, so the S app
 * tile that used to stand beside it showed the same symbol twice in one
 * lockup. The S on its own is for places too small for the word (the
 * browser tab, the notification badge), never next to it.
 * <p>
 * One component for both apps, so they cannot drift apart. Compact, so it
 * fits an app bar beside the menu and the bell on a 320px phone.
 */
export function BrandStrip({ app, label }: { app: 'rider' | 'partner'; label: string }) {
  return (
    <div className="flex min-w-0 flex-col items-start motion-safe:animate-fade-slide-in" data-testid="brand-strip" data-app={app}>
      {/* The wordmark from the logo sheet, not the name typed in a font. */}
      <SheOutWordmark tone="flow" className="w-[7rem]" />
      <span className="mt-0.5 inline-flex min-w-0 max-w-full items-center gap-1 text-micro font-bold uppercase tracking-[0.12em] text-primary">
        <span className="relative flex h-1.5 w-1.5 shrink-0">
          <span className="absolute inset-0 rounded-full bg-accent-orange motion-safe:animate-pulse-ring" />
          <span className="relative h-1.5 w-1.5 rounded-full bg-accent-orange" />
        </span>
        <span className="truncate">{label}</span>
      </span>
    </div>
  );
}
