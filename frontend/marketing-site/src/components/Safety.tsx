import { BadgeCheck, KeyRound, Radio, ShieldAlert, UserCheck, Phone } from 'lucide-react';
import { SAFETY } from '../content';
import { stagger } from '../lib/motion';

const card = 'relative overflow-hidden rounded-[28px] border border-white/10 bg-white/[0.06] p-7 backdrop-blur-sm transition-colors duration-300 hover:bg-white/[0.09]';
const byKey = Object.fromEntries(SAFETY.cards.map((c) => [c.key, c]));

function CardText({ k, icon: Icon }: { k: string; icon: typeof BadgeCheck }) {
  const c = byKey[k];
  return (
    <>
      <span className="grid h-12 w-12 place-items-center rounded-2xl bg-white/10 text-accent-orange ring-1 ring-white/15">
        <Icon className="h-6 w-6" aria-hidden />
      </span>
      <h3 className="mt-5 text-xl font-bold sm:text-2xl">{c.title}</h3>
      <p className="mt-2 leading-relaxed text-white/75">{c.body}</p>
    </>
  );
}

/** The checks a partner passes, ticking themselves off as the card arrives. */
function Checklist() {
  const items = ['Government ID + live selfie', 'Driving licence and RC', 'Vehicle insurance', 'Reviewed by a person'];
  return (
    <ul className="mt-6 grid gap-2.5 sm:grid-cols-2">
      {items.map((t, i) => (
        <li
          key={t}
          className="flex items-center gap-2.5 rounded-xl bg-white/[0.07] px-3.5 py-2.5 text-[15px] font-medium seq"
          style={{ ['--sd' as string]: `${400 + i * 140}ms` }}
        >
          <BadgeCheck className="h-5 w-5 shrink-0 text-accent-green" aria-hidden />
          {t}
        </li>
      ))}
    </ul>
  );
}

/** A trip line drawing itself between pickup and drop, with a rider dot riding it. */
function RouteArt() {
  return (
    <svg viewBox="0 0 320 120" className="mt-6 h-28 w-full" aria-hidden>
      <path d="M18 92 C 80 92, 90 30, 160 40 S 250 100, 302 26" fill="none" stroke="rgba(255,255,255,0.12)" strokeWidth="10" strokeLinecap="round" />
      <path
        className="route-path"
        style={{ ['--route-length' as string]: 360 }}
        d="M18 92 C 80 92, 90 30, 160 40 S 250 100, 302 26"
        fill="none"
        stroke="url(#routeGrad)"
        strokeWidth="5"
        strokeLinecap="round"
      />
      <defs>
        <linearGradient id="routeGrad" x1="0" x2="1">
          <stop offset="0" stopColor="#B69BFF" />
          <stop offset="1" stopColor="#FF8A3D" />
        </linearGradient>
      </defs>
      <circle cx="18" cy="92" r="9" fill="#7B4FD6" stroke="#fff" strokeWidth="3" />
      <circle cx="302" cy="26" r="9" fill="#F96717" stroke="#fff" strokeWidth="3" />
    </svg>
  );
}

function PickupCode() {
  return (
    <div className="mt-6 flex gap-2.5" aria-hidden>
      {['4', '8', '2', '7'].map((d, i) => (
        <span
          key={i}
          className="grid h-14 w-12 place-items-center rounded-xl bg-white font-heading text-2xl font-bold text-primary shadow-[0_10px_24px_-12px_rgba(0,0,0,0.6)] seq seq-pop"
          style={{ ['--sd' as string]: `${450 + i * 160}ms` }}
        >
          {d}
        </span>
      ))}
    </div>
  );
}

/** The SOS button, with the waves that go out when it is pressed. */
function SosArt() {
  return (
    <div className="relative mx-auto mt-8 grid h-40 w-40 place-items-center" aria-hidden>
      {[0, 0.8, 1.6].map((d) => (
        <span key={d} className="absolute inset-6 rounded-full bg-accent-red/40 motion-safe:animate-sos-wave" style={{ animationDelay: `${d}s` }} />
      ))}
      <span className="relative grid h-24 w-24 place-items-center rounded-full bg-gradient-to-b from-[#ff4b55] to-accent-red font-heading text-2xl font-extrabold tracking-wide shadow-[0_18px_40px_-10px_rgba(214,19,31,0.8)] ring-8 ring-white/10">
        SOS
      </span>
    </div>
  );
}

export function Safety() {
  return (
    <section id="safety" className="relative overflow-hidden bg-[#1a0942] py-24 text-white sm:py-32">
      <div className="absolute -right-40 top-0 h-[40rem] w-[40rem] rounded-full bg-primary-mid/30 blur-[140px] motion-safe:animate-drift" aria-hidden />
      <div className="absolute -left-32 bottom-0 h-[30rem] w-[30rem] rounded-full bg-accent-brand-orange/20 blur-[140px] motion-safe:animate-drift-slow" aria-hidden />

      <div className="wrap relative">
        <div className="max-w-3xl">
          <p className="eyebrow !text-accent-orange" data-reveal>
            {SAFETY.eyebrow}
          </p>
          <h2 className="section-title mt-4" data-reveal style={stagger(1)}>
            {SAFETY.title}
          </h2>
        </div>

        <div className="mt-16 grid gap-5 md:grid-cols-6">
          <div className={`${card} md:col-span-4`} data-reveal>
            <CardText k="checked" icon={BadgeCheck} />
            <Checklist />
          </div>
          <div className={`${card} md:col-span-2 md:row-span-2 flex flex-col bg-gradient-to-b from-accent-red/25 to-white/[0.04]`} data-reveal style={stagger(1)}>
            <CardText k="sos" icon={ShieldAlert} />
            <div className="flex flex-1 items-center">
              <SosArt />
            </div>
            <p className="mt-4 flex items-center justify-center gap-2 text-sm font-medium text-white/70">
              <Phone className="h-4 w-4" aria-hidden /> 112 is one tap away too
            </p>
          </div>
          <div className={`${card} md:col-span-2`} data-reveal style={stagger(2)}>
            <CardText k="code" icon={KeyRound} />
            <PickupCode />
          </div>
          <div className={`${card} md:col-span-2`} data-reveal style={stagger(3)}>
            <CardText k="share" icon={Radio} />
            <RouteArt />
          </div>
          <div className={`${card} md:col-span-6 md:flex md:items-center md:gap-10`} data-reveal style={stagger(4)}>
            <div className="md:flex-1">
              <CardText k="riders" icon={UserCheck} />
            </div>
            <p className="mt-6 rounded-2xl bg-white/[0.07] px-5 py-4 text-white/80 md:mt-0 md:max-w-sm">
              SheOut is women-only on both sides of every trip - the rider and the partner.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
