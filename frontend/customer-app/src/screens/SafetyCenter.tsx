import { BadgeCheck, Copy, HelpCircle, MapPin, PhoneCall, Siren, Vibrate, WifiOff } from 'lucide-react';
import { useMemo, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGoBack } from '../lib/useGoBack';
import { Button, Card, IconCircle, SafetyText, TopHeader, showToast, useAppLanguage, useTranslation } from '@sheout/design-system';
import { DiscreetSosToggle } from '../components/DiscreetSosToggle';
import { InsuredTripsExplainer } from '../components/InsuredTripsExplainer';
import { localEmergencyNumber } from '../lib/emergency';

/** What an iPhone Back Tap or Android quick-tap shortcut opens: the SOS countdown. */
const SHORTCUT_LINK = `${typeof window !== 'undefined' ? window.location.origin : 'https://app.sheoutride.com'}/sos?trigger=shortcut`;

/**
 * Safety Center: how SheOut's safety actually works, in plain words - what
 * verification checks and who does it, exactly what sharing a trip sends,
 * what SOS does step by step (including the discreet gestures and what
 * happens without data), when to call 112 instead, and what to do in the
 * situations people actually worry about.
 * <p>
 * Every line is safety copy (SafetyText): in Hindi and Telugu, which have not
 * yet been checked by a native speaker, the English is shown under each line,
 * and the page says so at the top. See docs/SAFETY_TRANSLATION_REVIEW.md.
 * The help assistant answers from the same facts (backend
 * assistant/knowledge.md) - change both together.
 */
export function SafetyCenter() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack('/home');
  const lng = useAppLanguage();
  const emergency = useMemo(localEmergencyNumber, []);
  const number = { number: emergency.number };

  return (
    <div className="space-y-5 pb-6">
      <TopHeader variant="back" title={t('safetyCenter.title')} onBack={goBack} />

      <Card variant="primary" className="space-y-2">
        <p className="font-heading text-title"><SafetyText k="safetyCenter.introTitle" englishClassName="font-normal" /></p>
        <p className="text-sm opacity-90"><SafetyText k="safetyCenter.intro" /></p>
      </Card>

      {/* Only while a passenger policy is in force. */}
      <InsuredTripsExplainer />

      {lng !== 'en' && (
        <p className="rounded-input bg-accent-orange-tint px-4 py-3 text-sm text-text-primary" data-testid="safety-center-review-note">
          {t('safetyCenter.pendingReview')}
        </p>
      )}

      <Section icon={<Siren />} title="safetyCenter.sos.title" testId="sc-sos">
        <Steps keys={['safetyCenter.sos.step1', 'safetyCenter.sos.step2', 'safetyCenter.sos.step3', 'safetyCenter.sos.step4']} values={number} />
        <Line k="safetyCenter.sos.contacts" />
        <Button fullWidth size="md" variant="danger" icon={<Siren className="h-4 w-4" />} onClick={() => navigate('/sos')}>
          <SafetyText k="safetyCenter.sos.open" englishClassName="font-normal" />
        </Button>
      </Section>

      <Section icon={<Vibrate />} title="safetyCenter.discreet.title" testId="sc-discreet">
        <Line k="safetyCenter.discreet.how" />
        <Line k="safetyCenter.discreet.countdown" />
        <Line k="safetyCenter.discreet.limit" />
        <DiscreetSosToggle />
        <div className="space-y-2 rounded-input bg-background p-3">
          <p className="text-sm font-semibold text-text-primary"><SafetyText k="safetyCenter.discreet.shortcutTitle" /></p>
          <Steps keys={['safetyCenter.discreet.ios1', 'safetyCenter.discreet.ios2', 'safetyCenter.discreet.ios3']} />
          <Line k="safetyCenter.discreet.android" />
          <button
            type="button"
            className="flex w-full items-center justify-between gap-2 rounded-input border border-border bg-surface px-3 py-2 text-left text-xs text-text-primary"
            onClick={async () => {
              try {
                await navigator.clipboard?.writeText(SHORTCUT_LINK);
                showToast(t('safetyCenter.linkCopied'));
              } catch {
                showToast(SHORTCUT_LINK);
              }
            }}
            data-testid="sc-copy-shortcut"
          >
            <span className="min-w-0 break-all font-mono">{SHORTCUT_LINK}</span>
            <Copy className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
          </button>
        </div>
      </Section>

      <Section icon={<WifiOff />} title="safetyCenter.noData.title" testId="sc-no-data">
        <Line k="safetyCenter.noData.slow" />
        <Line k="safetyCenter.noData.none" />
        <Line k="safetyCenter.noData.send" />
      </Section>

      <Section icon={<PhoneCall />} title="safetyCenter.vs112.title" values={number} testId="sc-112">
        <Line k="safetyCenter.vs112.call" values={number} />
        <Line k="safetyCenter.vs112.sos" />
        <Line k="safetyCenter.vs112.both" values={number} />
      </Section>

      <Section icon={<BadgeCheck />} title="safetyCenter.verification.title" testId="sc-verification">
        <Line k="safetyCenter.verification.riders" />
        <Line k="safetyCenter.verification.partners" />
        <Line k="safetyCenter.verification.people" />
        <Line k="safetyCenter.verification.pickupCode" />
      </Section>

      <Section icon={<MapPin />} title="safetyCenter.sharing.title" testId="sc-sharing">
        <Line k="safetyCenter.sharing.what" />
        <Line k="safetyCenter.sharing.snapshot" />
        <Line k="safetyCenter.sharing.who" />
      </Section>

      <Section icon={<HelpCircle />} title="safetyCenter.whatIf.title" testId="sc-what-if">
        {(['uncomfortable', 'cantReach', 'noSignal', 'mismatch'] as const).map((key) => (
          <div key={key} className="space-y-1 rounded-input bg-background p-3">
            <p className="text-sm font-semibold text-text-primary"><SafetyText k={`safetyCenter.whatIf.${key}.q`} /></p>
            <p className="text-sm text-text-secondary"><SafetyText k={`safetyCenter.whatIf.${key}.a`} values={number} /></p>
          </div>
        ))}
      </Section>

      <Button fullWidth variant="secondary" onClick={() => navigate('/help/assistant')}>
        {t('safetyCenter.askHelp')}
      </Button>
    </div>
  );
}

function Section({ icon, title, values, testId, children }: { icon: ReactNode; title: string; values?: Record<string, unknown>; testId: string; children: ReactNode }) {
  return (
    <Card className="space-y-3" data-testid={testId}>
      <div className="flex items-center gap-3">
        <IconCircle tone="soft" size="sm" icon={icon} />
        <h2 className="font-heading text-card-title text-text-primary"><SafetyText k={title} values={values} englishClassName="font-normal" /></h2>
      </div>
      {children}
    </Card>
  );
}

function Line({ k, values }: { k: string; values?: Record<string, unknown> }) {
  return (
    <p className="text-sm leading-relaxed text-text-secondary">
      <SafetyText k={k} values={values} />
    </p>
  );
}

function Steps({ keys, values }: { keys: string[]; values?: Record<string, unknown> }) {
  return (
    <ol className="space-y-2">
      {keys.map((k, i) => (
        <li key={k} className="flex gap-3 text-sm text-text-primary">
          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary text-micro text-text-inverse">{i + 1}</span>
          <span className="pt-0.5"><SafetyText k={k} values={values} /></span>
        </li>
      ))}
    </ol>
  );
}
