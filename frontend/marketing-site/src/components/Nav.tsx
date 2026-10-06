import { useEffect, useRef, useState } from 'react';
import { Menu, X } from 'lucide-react';
import { SheOutWordmark } from '../../../design-system/src/components/SheOutLogo';
import { LINKS, NAV } from '../content';
import { useScrollY } from '../lib/motion';

/**
 * Clear over the hero video, then a frosted white bar once the page moves.
 * On phones the links live in a sheet that closes on Escape, on a link, or on
 * the backdrop, and the page behind it stops scrolling while it is open.
 */
export function Nav() {
  const solid = useScrollY() > 24;
  const [open, setOpen] = useState(false);
  const toggle = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        toggle.current?.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
    };
  }, [open]);

  const light = solid || open;
  return (
    <header
      className={`fixed inset-x-0 top-0 z-50 transition-[background-color,box-shadow,backdrop-filter] duration-300 ${
        light ? 'bg-white/85 shadow-[0_8px_30px_-18px_rgba(36,10,80,0.45)] backdrop-blur-xl' : 'bg-transparent'
      }`}
    >
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-3 focus:rounded-full focus:bg-white focus:px-4 focus:py-2 focus:text-primary">
        Skip to content
      </a>
      <nav className="wrap flex h-16 items-center justify-between sm:h-[72px]" aria-label="Main">
        <a href="#top" className="flex items-center" aria-label="SheOut, back to top">
          <SheOutWordmark tone={light ? 'brand' : 'white'} className="w-[112px] transition-opacity sm:w-[124px]" />
        </a>

        <ul className="hidden items-center gap-1 lg:flex">
          {NAV.map((n) => (
            <li key={n.href}>
              <a
                href={n.href}
                className={`group relative rounded-full px-4 py-2 text-[15px] font-medium transition-colors ${
                  light ? 'text-text-primary hover:text-primary' : 'text-white/90 hover:text-white'
                }`}
              >
                {n.label}
                <span className="absolute inset-x-4 bottom-1 h-0.5 origin-left scale-x-0 rounded-full bg-current transition-transform duration-300 ease-out-expo group-hover:scale-x-100" />
              </a>
            </li>
          ))}
        </ul>

        <div className="flex items-center gap-2">
          <a href={LINKS.riderApp} className={`hidden sm:inline-flex ${light ? 'btn-primary' : 'btn-light'} !min-h-[44px] !px-5 !text-[15px]`}>
            Book a ride
          </a>
          <button
            ref={toggle}
            type="button"
            className={`grid h-11 w-11 place-items-center rounded-full lg:hidden ${light ? 'text-text-primary hover:bg-primary-light' : 'text-white hover:bg-white/15'}`}
            aria-expanded={open}
            aria-controls="mobile-menu"
            aria-label={open ? 'Close menu' : 'Open menu'}
            onClick={() => setOpen((o) => !o)}
          >
            {open ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
          </button>
        </div>
      </nav>

      {/* Phone menu: a sheet dropping from under the bar. */}
      <div
        id="mobile-menu"
        className={`lg:hidden ${open ? 'pointer-events-auto' : 'pointer-events-none'}`}
        aria-hidden={!open}
      >
        <div
          className={`fixed inset-0 top-16 bg-[#160735]/40 backdrop-blur-sm transition-opacity duration-300 ${open ? 'opacity-100' : 'opacity-0'}`}
          onClick={() => setOpen(false)}
        />
        <div
          className={`absolute inset-x-0 top-16 origin-top border-t border-border bg-white px-5 pb-6 pt-2 shadow-overlay transition-[transform,opacity] duration-300 ease-out-expo ${
            open ? 'translate-y-0 opacity-100' : '-translate-y-3 opacity-0'
          }`}
        >
          <ul>
            {NAV.map((n, i) => (
              <li key={n.href} className="border-b border-border last:border-0">
                <a
                  href={n.href}
                  tabIndex={open ? 0 : -1}
                  onClick={() => setOpen(false)}
                  className="flex min-h-[52px] items-center font-heading text-lg font-semibold text-text-primary"
                  style={{ transitionDelay: `${i * 30}ms` }}
                >
                  {n.label}
                </a>
              </li>
            ))}
          </ul>
          <div className="mt-4 grid gap-3">
            <a href={LINKS.riderApp} tabIndex={open ? 0 : -1} className="btn-primary w-full">
              Book a ride
            </a>
            <a href={LINKS.partnerApp} tabIndex={open ? 0 : -1} className="btn-outline w-full">
              Drive with SheOut
            </a>
          </div>
        </div>
      </div>
    </header>
  );
}
