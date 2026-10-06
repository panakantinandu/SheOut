import { useRef } from 'react';
import { STATS } from '../content';
import { stagger, useCountUp, useInView } from '../lib/motion';

function Stat({ value, suffix, label, start, i }: { value: number; suffix: string; label: string; start: boolean; i: number }) {
  const n = useCountUp(value, start);
  return (
    <div className="text-center" data-reveal style={stagger(i)}>
      {/* Screen readers get the final number, not every frame of the count. */}
      <p className="font-heading text-5xl font-extrabold tracking-tight text-flow sm:text-6xl" aria-hidden>
        {n}
        {suffix}
      </p>
      <p className="sr-only">
        {value}
        {suffix} {label}
      </p>
      <p className="mt-2 text-text-secondary" aria-hidden>
        {label}
      </p>
    </div>
  );
}

/** Four true numbers about how SheOut works - nothing that needs a footnote. */
export function Stats() {
  const ref = useRef<HTMLDivElement>(null);
  const seen = useInView(ref, '0px 0px -20% 0px');
  return (
    <section className="py-20 sm:py-24">
      <div ref={ref} className="wrap grid grid-cols-2 gap-y-12 lg:grid-cols-4">
        {STATS.map((s, i) => (
          <Stat key={s.label} {...s} start={seen} i={i} />
        ))}
      </div>
    </section>
  );
}
