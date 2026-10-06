import { useId, useState } from 'react';
import { Plus } from 'lucide-react';
import { FAQ, LINKS } from '../content';
import { stagger } from '../lib/motion';

/** One question open at a time; each is a real button with aria-expanded. */
export function Faq() {
  const [open, setOpen] = useState<number | null>(0);
  const base = useId();
  return (
    <section id="faq" className="bg-gradient-to-b from-background to-primary-light/40 py-24 sm:py-32">
      <div className="wrap grid gap-12 lg:grid-cols-[0.8fr_1.2fr] lg:gap-20">
        <div className="lg:sticky lg:top-28 lg:self-start">
          <p className="eyebrow" data-reveal>
            Questions
          </p>
          <h2 className="section-title mt-4" data-reveal style={stagger(1)}>
            Good to know before your first ride.
          </h2>
          <p className="mt-5 text-lg text-text-secondary" data-reveal style={stagger(2)}>
            Something else? Write to us at{' '}
            <a href={LINKS.support} className="font-semibold text-primary underline decoration-primary/30 underline-offset-4 hover:decoration-primary">
              {LINKS.supportEmail}
            </a>
            .
          </p>
        </div>

        <ul className="divide-y divide-primary/10 rounded-[28px] bg-white px-6 shadow-lift ring-1 ring-primary/5 sm:px-8" data-reveal="zoom">
          {FAQ.map((f, i) => {
            const isOpen = open === i;
            const id = `${base}-${i}`;
            return (
              <li key={f.q}>
                <h3>
                  <button
                    type="button"
                    className="flex w-full items-center justify-between gap-6 py-6 text-left font-heading text-lg font-semibold sm:text-xl"
                    aria-expanded={isOpen}
                    aria-controls={id}
                    onClick={() => setOpen(isOpen ? null : i)}
                  >
                    {f.q}
                    <span
                      className={`grid h-9 w-9 shrink-0 place-items-center rounded-full transition-[transform,background-color,color] duration-300 ease-out-expo ${
                        isOpen ? 'rotate-45 bg-primary text-white' : 'bg-primary-light text-primary'
                      }`}
                    >
                      <Plus className="h-5 w-5" aria-hidden />
                    </span>
                  </button>
                </h3>
                <div id={id} className="collapse-rows" data-open={isOpen} role="region" aria-label={f.q}>
                  <div>
                    <p className={`pb-6 pr-12 text-[17px] leading-relaxed text-text-secondary transition-opacity duration-300 ${isOpen ? 'opacity-100' : 'opacity-0'}`}>
                      {f.a}
                    </p>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
