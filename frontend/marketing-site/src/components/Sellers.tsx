import { ArrowRight } from 'lucide-react';
import { LINKS, SELLERS } from '../content';
import { stagger } from '../lib/motion';
import { Device } from './Device';

/**
 * The seller story, with the marketplace screen from the customer app. The
 * "Sell on SheOut" walkthrough video came off the site on 2026-10-15: it still
 * showed the removed illustrations (docs/assets-licences.md). It comes back
 * once it is re-recorded from the current app.
 */
export function Sellers() {
  return (
    <section id="sellers" className="relative py-24 sm:py-32">
      <div className="wrap">
        <div className="relative overflow-hidden rounded-[36px] bg-gradient-to-br from-[#FDF2F8] via-white to-accent-orange-tint p-6 ring-1 ring-primary/5 sm:p-12 lg:p-16">
          <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full bg-[#F0ABFC]/40 blur-3xl" aria-hidden />
          <div className="relative grid items-center gap-12 lg:grid-cols-[1.1fr_1fr]">
            <div>
              <p className="eyebrow !text-[#BE185D]" data-reveal>
                {SELLERS.eyebrow}
              </p>
              <h2 className="section-title mt-4" data-reveal style={stagger(1)}>
                {SELLERS.title}
              </h2>
              <p className="mt-5 max-w-xl text-lg leading-relaxed text-text-secondary" data-reveal style={stagger(2)}>
                {SELLERS.body}
              </p>
              <div className="mt-8 flex flex-wrap items-end gap-x-6 gap-y-3" data-reveal style={stagger(3)}>
                <p className="font-heading text-4xl font-extrabold text-text-primary">{SELLERS.price}</p>
                <p className="pb-1.5 text-text-secondary">{SELLERS.priceNote}</p>
              </div>
              <a href={LINKS.sellers} className="btn-primary btn-sheen group mt-8" data-reveal style={stagger(4)}>
                {SELLERS.cta}
                <ArrowRight className="h-5 w-5 transition-transform duration-300 group-hover:translate-x-1" aria-hidden />
              </a>
            </div>

            <div className="relative mx-auto w-[min(78vw,300px)]" data-reveal="right">
              <Device
                screen="rider-marketplace"
                alt="The SheOut marketplace: shops run by women nearby, by category - fashion and sarees, beauty and more"
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
