import { SheOutAppIcon, SheOutWordmark } from './SheOutLogo';

/**
 * SheOut, by name, at the top of Home in both apps: the app's own icon
 * floating gently beside the wordmark from the logo sheet, and a short line
 * under it, with a pulsing dot, saying which app this is ("Partner") or what
 * SheOut is ("By women · For women").
 * <p>
 * Partners asked where they were - nothing on their Home said "SheOut" -
 * and the rider Home had the same gap. One component, so the two apps
 * cannot drift apart again. Compact, so it fits an app bar beside the menu
 * and the bell on a 360px phone.
 */
export function BrandStrip({ app, label }: { app: 'rider' | 'partner'; label: string }) {
  return (
    <div className="flex min-w-0 items-center gap-2 motion-safe:animate-fade-slide-in" data-testid="brand-strip">
      <SheOutAppIcon app={app} className="h-9 w-9 shrink-0 shadow-lift motion-safe:animate-float" />
      <span className="flex min-w-0 flex-col">
        {/* The wordmark from the logo sheet, not the name typed in a font. */}
        <SheOutWordmark className="w-[5.75rem]" />
        <span className="mt-0.5 inline-flex min-w-0 items-center gap-1 text-micro font-bold uppercase tracking-[0.12em] text-primary">
          <span className="relative flex h-1.5 w-1.5 shrink-0">
            <span className="absolute inset-0 rounded-full bg-accent-orange motion-safe:animate-pulse-ring" />
            <span className="relative h-1.5 w-1.5 rounded-full bg-accent-orange" />
          </span>
          <span className="truncate">{label}</span>
        </span>
      </span>
    </div>
  );
}
