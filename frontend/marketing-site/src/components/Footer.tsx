import { ArrowRight, Mail } from 'lucide-react';
import { SheOutWordmark } from '../../../design-system/src/components/SheOutLogo';
import womenArt from '../../../design-system/src/assets/art/women.webp';
import { LINKS, NAV } from '../content';
import { stagger } from '../lib/motion';

/** The last ask, then the footer - one dark block so the page ends on the brand. */
export function Footer() {
  return (
    <footer className="relative overflow-hidden bg-[#160735] text-white">
      <div className="absolute left-1/2 top-0 h-[36rem] w-[60rem] -translate-x-1/2 rounded-full bg-primary-mid/25 blur-[140px]" aria-hidden />

      <div className="wrap relative pt-24 sm:pt-32">
        <div className="grid items-end gap-10 lg:grid-cols-[1.2fr_1fr]">
          <div>
            <h2 className="text-[2.5rem] font-extrabold leading-[1.05] tracking-tight sm:text-6xl" data-reveal>
              Your ride is <span className="text-flow-light">waiting.</span>
            </h2>
            <p className="mt-5 max-w-lg text-lg text-white/75" data-reveal style={stagger(1)}>
              Open SheOut on your phone and book your first ride in three taps. No store download needed.
            </p>
            <div className="mt-9 flex flex-col gap-3 sm:flex-row" data-reveal style={stagger(2)}>
              <a href={LINKS.riderApp} className="btn-light btn-sheen group">
                Book a ride
                <ArrowRight className="h-5 w-5 transition-transform duration-300 group-hover:translate-x-1" aria-hidden />
              </a>
              <a href={LINKS.partnerApp} className="btn-ghost-light">
                Drive with SheOut
              </a>
            </div>
          </div>
          <img src={womenArt} alt="" loading="lazy" className="mx-auto w-full max-w-[460px] motion-safe:animate-float-slow" data-reveal="zoom" />
        </div>

        <div className="mt-20 grid gap-10 border-t border-white/10 py-12 sm:grid-cols-2 lg:grid-cols-4">
          <div className="lg:col-span-2">
            <SheOutWordmark tone="white" className="w-36" />
            <p className="mt-4 max-w-sm text-white/65">Rides and deliveries built around women's safety, with women riders and women partners. Hyderabad.</p>
            <a href={LINKS.support} className="mt-5 inline-flex items-center gap-2 text-white/85 hover:text-white">
              <Mail className="h-4 w-4" aria-hidden />
              {LINKS.supportEmail}
            </a>
          </div>
          <nav aria-label="Footer">
            <p className="font-heading font-semibold">Explore</p>
            <ul className="mt-4 space-y-3 text-white/70">
              {NAV.map((n) => (
                <li key={n.href}>
                  <a href={n.href} className="hover:text-white">
                    {n.label}
                  </a>
                </li>
              ))}
            </ul>
          </nav>
          <div>
            <p className="font-heading font-semibold">Apps and legal</p>
            <ul className="mt-4 space-y-3 text-white/70">
              <li><a href={LINKS.riderApp} className="hover:text-white">Rider app</a></li>
              <li><a href={LINKS.partnerApp} className="hover:text-white">Partner app</a></li>
              <li><a href={LINKS.terms} className="hover:text-white">Terms of Service</a></li>
              <li><a href={LINKS.privacy} className="hover:text-white">Privacy Policy</a></li>
            </ul>
          </div>
        </div>
        <p className="border-t border-white/10 py-8 text-sm text-white/50">&copy; {new Date().getFullYear()} SheOut. By women, for women.</p>
      </div>
    </footer>
  );
}
