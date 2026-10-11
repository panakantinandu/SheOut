import {
  AlertCircle,
  Camera,
  Car,
  Check,
  ChevronDown,
  ClipboardList,
  Clock,
  ExternalLink,
  FileCheck2,
  Gauge,
  IdCard,
  Lock,
  ScrollText,
  ShieldCheck,
  UserRound,
  Wrench,
} from 'lucide-react';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGoBack } from '../lib/useGoBack';
import {
  Avatar,
  Button,
  Card,
  DocumentMasker,
  LiveSelfieCapture,
  LoadFailed,
  SafetyText,
  PARTNER_VERIFICATION_CONSENT,
  SkeletonCard,
  TextField,
  TopHeader,
  highlightPlaceholders,
  shrinkPhoto,
  showToast,
  useTranslation,
} from '@sheout/design-system';
import { photoWarningsParam, type LiveSelfieResult, type PhotoWarning } from '@sheout/design-system';
import { ApiError, usersApi, verificationApi } from '../api/client';
import type {
  ConsentStatus,
  DocumentState,
  DriverProfileSummary,
  PartnerDocumentType,
  PartnerReadiness,
  VerificationTurnaround,
  ReadinessBlocker,
  VerificationSummary,
} from '../api/types';
import { ApplicantFacts, DocumentForm } from '../components/DocumentStep';
import { RejectionFix, WaitLine } from '../components/verification/RejectionFix';
import { findFaceIn } from '../lib/faceMatch';
import { PhotoPicker } from '../components/verification/PhotoPicker';
import { blockerText } from '../lib/readinessText';
import { daysUntil, formatDay } from '../lib/documentDates';

/** Telangana Police's Police Verification Certificate portal (i-Verify). */
const TS_POLICE_PORTAL = 'https://pvc.tspolice.gov.in/';

/**
 * Where a step stands, in the only terms she needs: finished, with us, hers
 * to do, hers to fix, or waiting on step 1. "soon" is approved but running
 * out within 30 days - done, but worth doing again before it stops her.
 */
type StepState = 'done' | 'soon' | 'waiting' | 'fix' | 'todo' | 'locked';

type Section = 'you' | 'vehicle' | 'police';

interface Step {
  key: string;
  section: Section;
  title: string;
  state: StepState;
  /** One line under the title: where it stands, or why it needs her. */
  summary: string;
  /** In place of summary when it needs more than a string: a fix in her language, with the English beneath. */
  summaryNode?: ReactNode;
  body: ReactNode;
}

/** The four stages she moves through; the first three are the sections of steps. */
type Stage = Section | 'ready';
const STAGES: Stage[] = ['you', 'vehicle', 'police', 'ready'];
type StageState = 'done' | 'waiting' | 'fix' | 'todo';

/** Where one stage stands, from its steps: fixing beats waiting beats done. */
function stageStateOf(steps: Step[]): StageState {
  if (steps.length === 0) return 'done';
  if (steps.some((s) => s.state === 'fix')) return 'fix';
  if (steps.every((s) => s.state === 'done' || s.state === 'soon')) return 'done';
  if (steps.every((s) => s.state === 'done' || s.state === 'soon' || s.state === 'waiting')) return 'waiting';
  return 'todo';
}

type Translate = (key: string, options?: Record<string, unknown>) => string;

/**
 * REAL: getting verified, as one checklist she can finish without help.
 * <p>
 * What made the old screen hard was not the number of documents - all of
 * them are needed - but that every step was open at once, each with its own
 * paragraphs, so nothing said "do this now". Now:
 * <ul>
 *   <li>One card on top: how far she is ("3 of 8 done"), and the one next
 *       thing, with a Continue button that opens it. When everything is
 *       sent, it says nothing is needed from her and that she will be
 *       notified - so a wait does not read as something she missed.</li>
 *   <li>"Keep these ready" - every paper she will need, before she starts,
 *       so she gathers them once instead of finding out one at a time; and
 *       the police certificate flagged first, because it takes days.</li>
 *   <li>The steps grouped as About you / You and your vehicle / Police
 *       check, each a single row (a tick, a clock, a warning or its number)
 *       that opens to just what that step needs. Only the next step is open
 *       at first; after she sends one, the next opens by itself.</li>
 *   <li>Every form takes a photo straight from the camera, shows it back,
 *       and says where on the paper each answer is printed.</li>
 * </ul>
 * Where she stands comes from GET /users/driver/me/readiness - the same
 * answer going online is decided on - so this screen cannot tell her she is
 * ready while the server refuses her. Nothing here changes what is checked.
 * <p>
 * Consent first: nothing can be sent until she has agreed to SheOut
 * verifying her identity, documents and police record. She sees a plain
 * summary with the full wording one tap away (PARTNER_VERIFICATION_CONSENT,
 * still marked for the lawyer).
 */
export function Verification() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack('/home');
  const [summary, setSummary] = useState<VerificationSummary | null>(null);
  const [readiness, setReadiness] = useState<PartnerReadiness | null>(null);
  const [readinessFailed, setReadinessFailed] = useState(false);
  const [consent, setConsent] = useState<ConsentStatus | null>(null);
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  /** The step she opened; undefined follows the next step, so it moves on by itself after a send. */
  const [openKey, setOpenKey] = useState<string | null | undefined>(undefined);
  const [showReady, setShowReady] = useState<boolean | null>(null);
  /** The stage she tapped on the tracker; null follows the stage of her next step. */
  const [stageOpen, setStageOpen] = useState<Stage | null>(null);
  const [turnaround, setTurnaround] = useState<VerificationTurnaround | null>(null);
  const rows = useRef<Record<string, HTMLDivElement | null>>({});

  function load() {
    verificationApi
      .getMyStatus()
      .then(setSummary)
      // Her language, never the server's English: see LoadFailed.
      .catch(() => setError(t('verification.loadError')));
    usersApi
      .getReadiness()
      .then((r) => {
        setReadiness(r);
        setReadinessFailed(false);
      })
      .catch(() => setReadinessFailed(true));
    verificationApi.getConsent().then(setConsent).catch(() => {});
    verificationApi.turnaround().then(setTurnaround).catch(() => {});
    usersApi.getMyProfile().then(setProfile).catch(() => {});
  }

  useEffect(load, []);

  /** A step was sent: say so, reload, and let the next step open by itself. */
  function sent() {
    showToast(t('verify.sentToast'));
    setOpenKey(undefined);
    setStageOpen(null);
    load();
  }

  const loaded = summary && (readiness || readinessFailed);
  if (!loaded) {
    return (
      <div className="space-y-6">
        <TopHeader variant="back" title={t('verify.title')} onBack={goBack} />
        {error ? (
          <LoadFailed title={error} onRetry={() => { setError(null); load(); }} testId="verify-load-failed" />
        ) : (
          <SkeletonCard lines={4} label={t('verification.loading')} />
        )}
      </div>
    );
  }

  const consented = Boolean(consent?.current);
  const hasPhoto = Boolean(profile?.hasProfilePhoto);
  const blockers = readiness?.blockers ?? [];
  const warnings = readiness?.warnings ?? [];
  const docState = (type: PartnerDocumentType) => readiness?.documents.find((d) => d.type === type);
  const vehicleDocs = (readiness?.documents ?? []).filter((d) => d.required && d.type !== 'POLICE_CERTIFICATE');

  const steps: Step[] = [
    {
      key: 'consent',
      section: 'you',
      title: t('verify.consent.title'),
      state: consented ? 'done' : 'todo',
      summary: consented
        ? t('verify.status.done')
        : consent?.acceptedVersion
          ? t('verify.consent.updated')
          : t('verify.status.todo'),
      body: <ConsentBody consent={consent} onAgreed={(c) => { setConsent(c); setOpenKey(undefined); load(); }} />,
    },
    idStep(t, summary, consented, sent),
    {
      key: 'photo',
      section: 'you',
      title: t('verify.photo.title'),
      state: hasPhoto ? 'done' : 'todo',
      summary: hasPhoto ? t('verify.photo.added') : t('verify.status.todo'),
      body: <PhotoBody profile={profile} onProfile={(p) => { setProfile(p); if (p.hasProfilePhoto) sent(); }} />,
    },
    ...vehicleDocs.map((d) => documentStep(t, d, consented, blockers, warnings, sent, profile?.vehicleRegistrationNumber, docState('VEHICLE_RC')?.status ?? null)),
    policeStep(t, summary, docState('POLICE_CERTIFICATE'), consented, blockers, warnings, sent),
  ];

  const total = steps.length;
  const done = steps.filter((s) => s.state === 'done' || s.state === 'soon').length;
  // Something sent back comes before something not yet done: it already
  // stopped a review, and she has the paper in hand from last time.
  const next =
    steps.find((s) => s.state === 'fix') ?? steps.find((s) => s.state === 'todo') ?? steps.find((s) => s.state === 'soon') ?? null;
  const waiting = steps.filter((s) => s.state === 'waiting').length;
  const ready = Boolean(readiness?.ready) && hasPhoto;
  const open = openKey === undefined ? next?.key ?? null : openKey;
  // Open by default only before she has sent anything: then it is the list she needs.
  const nothingSentYet = !summary.documentSubmitted && (readiness?.documents ?? []).every((d) => !d.status);
  const readyListOpen = showReady ?? nothingSentYet;

  function openStep(key: string) {
    setOpenKey(key);
    requestAnimationFrame(() => rows.current[key]?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }

  const sections: { id: Section; label: string }[] = [
    { id: 'you', label: t('verify.stage.you') },
    { id: 'vehicle', label: t('verify.stage.vehicle') },
    { id: 'police', label: t('verify.stage.police') },
  ];
  const stageStates: Record<Stage, StageState> = {
    you: stageStateOf(steps.filter((s) => s.section === 'you')),
    vehicle: stageStateOf(steps.filter((s) => s.section === 'vehicle')),
    police: stageStateOf(steps.filter((s) => s.section === 'police')),
    ready: ready ? 'done' : 'todo',
  };
  // The stage shown: the one she tapped, else the one her next step is in,
  // else the first not finished - so she sees one stage at a time.
  const shownStage: Stage = stageOpen ?? next?.section ?? STAGES.find((s) => stageStates[s] !== 'done') ?? 'ready';

  return (
    <ApplicantFacts.Provider value={{ name: profile?.name, dateOfBirth: profile?.dateOfBirth }}>
    <div className="space-y-6 pb-6">
      <TopHeader variant="back" title={t('verify.title')} onBack={goBack} />
      {error && <p className="text-sm text-danger">{error}</p>}

      {/* The four stages, where she is in them, and that nothing is lost if she stops. */}
      <StageTracker states={stageStates} shown={shownStage} onPick={(s) => { setStageOpen(s); setOpenKey(undefined); }} />
      <div className="space-y-1 px-1">
        <p className="text-caption text-text-secondary" data-testid="verify-saved">{t('verify.savedNote')}</p>
        {!ready && <WaitLine turnaround={turnaround} />}
      </div>

      {/* Where she is, and the one thing to do next. */}
      <Card tone={ready ? 'success' : 'default'} className="space-y-4" data-testid="readiness-card" data-state={ready ? 'ready' : next ? 'next' : 'waiting'}>
        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="font-semibold text-text-primary" data-testid="verify-progress">{t('verify.progress', { done, total })}</span>
            <span className="text-text-secondary">{Math.round((done / total) * 100)}%</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-background" aria-hidden="true">
            <div className="h-full rounded-full bg-primary transition-all duration-500" style={{ width: `${(done / total) * 100}%` }} />
          </div>
        </div>

        {ready ? (
          <div className="flex items-start gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent-green text-text-inverse">
              <ShieldCheck className="h-5 w-5" aria-hidden="true" />
            </span>
            <div className="flex-1">
              <p className="font-heading text-card-title text-text-primary">{t('verify.readyTitle')}</p>
              <p className="mt-1 text-sm text-text-secondary">{t('verify.readyBody')}</p>
              <Button size="md" className="mt-3" onClick={() => navigate('/home')}>
                {t('verify.goHome')}
              </Button>
            </div>
          </div>
        ) : next ? (
          <div>
            <p className="text-caption font-semibold uppercase tracking-wide text-text-secondary">{t('verify.nextLabel')}</p>
            <p className="mt-1 font-heading text-card-title text-text-primary" data-testid="verify-next">{next.title}</p>
            {next.state === 'fix' && (
              <p className="mt-1 text-sm text-danger">
                <span className="font-semibold">{t('verify.howToFix')}: </span>
                {next.summaryNode ?? next.summary}
              </p>
            )}
            <Button fullWidth className="mt-3" onClick={() => { setStageOpen(null); openStep(next.key); }} data-testid="verify-continue">
              {t('verify.continue')}
            </Button>
          </div>
        ) : (
          <div className="flex items-start gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-light text-primary">
              <Clock className="h-5 w-5" aria-hidden="true" />
            </span>
            <div className="flex-1">
              <p className="font-heading text-card-title text-text-primary">{t('verify.waitingTitle')}</p>
              <p className="mt-1 text-sm text-text-secondary">
                {waiting > 0 || !blockers[0] ? t('verify.waitingBody') : blockerText(t, blockers[0])}
              </p>
            </div>
          </div>
        )}
        {warnings.map((w, i) => (
          <p key={i} className="text-caption text-accent-orange-strong">{blockerText(t, w)}</p>
        ))}
        {readinessFailed && <p className="text-caption text-text-secondary">{t('verification.loadError')}</p>}
      </Card>

      {/* Everything she will need, before she starts. */}
      {!ready && (
        <Card className="space-y-3 p-4" data-testid="keep-ready">
          <button
            type="button"
            className="flex min-h-tap w-full items-center gap-3 text-left"
            aria-expanded={readyListOpen}
            onClick={() => setShowReady(!readyListOpen)}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-light text-primary">
              <ClipboardList className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className="flex-1 font-heading text-card-title text-text-primary">{t('verify.ready.title')}</span>
            <ChevronDown className={`h-5 w-5 text-text-secondary transition-transform ${readyListOpen ? 'rotate-180' : ''}`} aria-hidden="true" />
          </button>
          {readyListOpen && (
            <div className="space-y-3 text-sm">
              <p className="text-text-secondary">{t('verify.ready.intro')}</p>
              <ul className="space-y-2">
                {[
                  'id',
                  ...vehicleDocs.map((d) => READY_KEY[d.type]).filter((k): k is string => Boolean(k)),
                ].map((k) => (
                  <li key={k} className="flex items-start gap-2 text-text-primary">
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                    {t(`verify.ready.${k}`)}
                  </li>
                ))}
                <li className="flex items-start gap-2 text-text-primary">
                  <Check className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                  <span>
                    {t('verify.ready.police')}
                    <span className="mt-0.5 block text-caption font-medium text-accent-orange-strong">{t('verify.ready.policeTip')}</span>
                  </span>
                </li>
              </ul>
              <p className="rounded-input bg-background p-3 text-caption text-text-secondary">{t('verify.ready.photoTip')}</p>
            </div>
          )}
        </Card>
      )}

      {shownStage === 'ready' && (
        <Card className="space-y-2 p-4" data-testid="stage-ready">
          <p className="font-heading text-card-title text-text-primary">{ready ? t('verify.readyTitle') : t('verify.stage.ready')}</p>
          <p className="text-sm text-text-secondary">{ready ? t('verify.readyBody') : t('verify.stage.readyBody')}</p>
        </Card>
      )}
      {shownStage === 'police' && (
        <p className="px-1 text-caption text-text-secondary">
          <SafetyText k="verifyStage.policeWhy" />
        </p>
      )}

      {sections.map((section) => {
        const inSection = steps.filter((s) => s.section === section.id);
        if (inSection.length === 0 || section.id !== shownStage) return null;
        return (
          <section key={section.id} className="space-y-2">
            <h2 className="px-1 text-caption font-semibold uppercase tracking-wide text-text-secondary">{section.label}</h2>
            <Card className="divide-y divide-border p-0">
              {inSection.map((step) => (
                <StepRow
                  key={step.key}
                  rowRef={(el) => {
                    rows.current[step.key] = el;
                  }}
                  number={steps.indexOf(step) + 1}
                  step={step}
                  open={open === step.key}
                  onToggle={() => setOpenKey(open === step.key ? null : step.key)}
                />
              ))}
            </Card>
          </section>
        );
      })}

      {/* PAN: optional, and deliberately nothing to do with the checks above. */}
      <section className="space-y-2">
        <h2 className="px-1 text-caption font-semibold uppercase tracking-wide text-text-secondary">{t('verify.sections.optional')}</h2>
        <Card className="p-0">
          <PanRow profile={profile} onProfile={setProfile} open={open === 'pan'} onToggle={() => setOpenKey(open === 'pan' ? null : 'pan')} />
        </Card>
      </section>
    </div>
    </ApplicantFacts.Provider>
  );
}

const READY_KEY: Partial<Record<PartnerDocumentType, string>> = {
  DRIVING_LICENCE: 'licence',
  VEHICLE_RC: 'rc',
  VEHICLE_INSURANCE: 'insurance',
  FITNESS_CERTIFICATE: 'fitness',
};

const DOC_ICONS: Partial<Record<PartnerDocumentType, ReactNode>> = {
  DRIVING_LICENCE: <IdCard className="h-4 w-4" />,
  VEHICLE_RC: <Car className="h-4 w-4" />,
  VEHICLE_INSURANCE: <ShieldCheck className="h-4 w-4" />,
  PUC: <Gauge className="h-4 w-4" />,
  FITNESS_CERTIFICATE: <Wrench className="h-4 w-4" />,
  POLICE_CERTIFICATE: <FileCheck2 className="h-4 w-4" />,
};

// ---------------------------------------------------------------- steps

function idStep(t: Translate, summary: VerificationSummary, consented: boolean, onSent: () => void): Step {
  const status = summary.genderVerificationStatus;
  const state: StepState =
    status === 'VERIFIED' ? 'done' : status === 'UNDER_REVIEW' ? 'waiting' : !consented ? 'locked' : status === 'REJECTED' ? 'fix' : 'todo';
  return {
    key: 'id',
    section: 'you',
    title: t('verify.id.title'),
    state,
    summary:
      state === 'done'
        ? t('verify.status.approved')
        : state === 'waiting'
          ? t('verify.status.waiting')
          : state === 'locked'
            ? t('verify.status.locked')
            : state === 'fix'
              ? summary.rejectionReason
                ? t('verify.status.rejected', { reason: summary.rejectionReason })
                : t('verify.status.rejectedNoReason')
              : t('verify.status.todo'),
    summaryNode: state === 'fix' ? <RejectionFix code={summary.rejectionCode} reason={summary.rejectionReason} /> : undefined,
    body: state === 'todo' || state === 'fix' ? <IdBody onSent={onSent} /> : <StateNote state={state} why={t('verify.id.why')} />,
  };
}

function documentStep(
  t: Translate,
  d: DocumentState,
  consented: boolean,
  blockers: ReadinessBlocker[],
  warnings: ReadinessBlocker[],
  onSent: () => void,
  profilePlate?: string | null,
  rcStatus?: DocumentState['status'],
): Step {
  const days = d.validUntil ? daysUntil(d.validUntil) : null;
  const notCommercial =
    d.type === 'VEHICLE_INSURANCE' && blockers.some((b) => b.code === 'INSURANCE_NOT_COMMERCIAL');
  const expiring = warnings.some((w) => w.code === 'DOCUMENT_EXPIRING' && w.documentType === d.type);
  let state: StepState;
  let line: string;
  // Her PUC is read from the vehicle's government record when her RC is
  // approved - nothing to upload. Only if that record showed no valid PUC
  // (RC approved, PUC still missing) does she get one and upload it.
  const pucFromRecord = d.type === 'PUC' && !d.status && rcStatus !== 'VERIFIED';
  if (pucFromRecord) {
    state = consented ? 'waiting' : 'locked';
    line = consented ? t('verify.puc.fromRecord') : t('verify.status.locked');
  } else if (!d.status) {
    state = consented ? 'todo' : 'locked';
    line = consented ? (d.type === 'PUC' ? t('verify.puc.needed') : t('verify.status.todo')) : t('verify.status.locked');
  } else if (d.renewalUnderReview) {
    state = 'done';
    line = t('verify.status.renewal');
  } else if (d.status === 'PENDING' || d.status === 'UNDER_REVIEW') {
    state = 'waiting';
    line = t('verify.status.waiting');
  } else if (d.status === 'REJECTED') {
    state = 'fix';
    line = d.rejectionReason ? t('verify.status.rejected', { reason: d.rejectionReason }) : t('verify.status.rejectedNoReason');
  } else if (d.status === 'EXPIRED' || (days !== null && days < 0)) {
    state = 'fix';
    line = t('verify.status.expired', { date: formatDay(d.validUntil) });
  } else if (notCommercial) {
    state = 'fix';
    line = t('verify.status.notCommercial');
  } else if (expiring || (days !== null && days <= 30)) {
    state = 'soon';
    line = t('verify.status.expiring', { date: formatDay(d.validUntil) });
  } else {
    state = 'done';
    line = d.validUntil ? t('verify.status.approvedUntil', { date: formatDay(d.validUntil) }) : t('verify.status.approved');
  }
  return {
    key: d.type,
    section: 'vehicle',
    title: t(`verify.doc.type.${d.type}`),
    state,
    summary: line,
    summaryNode: d.status === 'REJECTED' ? <RejectionFix code={d.rejectionCode} reason={d.rejectionReason} type={d.type} /> : undefined,
    body: (
      <DocumentBody
        type={d.type}
        state={d}
        stepState={state}
        why={pucFromRecord ? t('verify.puc.fromRecordWhy') : t(`verify.doc.why.${d.type}`)}
        profilePlate={profilePlate}
        onSent={onSent}
      />
    ),
  };
}

function policeStep(
  t: Translate,
  summary: VerificationSummary,
  doc: DocumentState | undefined,
  consented: boolean,
  blockers: ReadinessBlocker[],
  warnings: ReadinessBlocker[],
  onSent: () => void,
): Step {
  const dueBlocker = blockers.find((b) => b.code === 'POLICE_REVERIFY_DUE');
  const dueWarning = warnings.find((b) => b.code === 'POLICE_REVERIFY_DUE');
  const check = summary.policeVerificationStatus;
  let state: StepState;
  let line: string;
  if (check === 'VERIFIED' && dueBlocker) {
    state = 'fix';
    line = t('verify.status.policeDue', { date: formatDay(dueBlocker.date) });
  } else if (check === 'VERIFIED' && dueWarning) {
    state = 'soon';
    line = t('verify.status.policeDue', { date: formatDay(dueWarning.date) });
  } else if (check === 'VERIFIED') {
    state = 'done';
    line = t('verify.status.approved');
  } else if (check === 'REJECTED' || doc?.status === 'REJECTED') {
    state = consented ? 'fix' : 'locked';
    line = doc?.rejectionReason ? t('verify.status.rejected', { reason: doc.rejectionReason }) : t('verify.status.rejectedNoReason');
  } else if (doc?.status) {
    state = 'waiting';
    line = t('verify.status.waiting');
  } else {
    state = consented ? 'todo' : 'locked';
    line = consented ? t('verify.status.todo') : t('verify.status.locked');
  }
  return {
    key: 'POLICE_CERTIFICATE',
    section: 'police',
    title: t('verify.doc.type.POLICE_CERTIFICATE'),
    state,
    summary: line,
    summaryNode: doc?.status === 'REJECTED'
      ? <RejectionFix code={doc.rejectionCode} reason={doc.rejectionReason} type="POLICE_CERTIFICATE" />
      : undefined,
    body: (
      <DocumentBody
        type="POLICE_CERTIFICATE"
        state={doc}
        stepState={state}
        why={t('verify.doc.why.POLICE_CERTIFICATE')}
        onSent={onSent}
        extra={state === 'todo' || state === 'fix' || state === 'soon' ? <PoliceHowTo /> : null}
      />
    ),
  };
}

// ---------------------------------------------------------------- stages

/**
 * Identity, Licence and vehicle, Safety check, Ready: where she is, at a
 * glance, and a way to look at any stage. A tick for a finished stage, a
 * clock for one being checked, a warning for one that needs a fix.
 */
function StageTracker({ states, shown, onPick }: { states: Record<Stage, StageState>; shown: Stage; onPick: (s: Stage) => void }) {
  const { t } = useTranslation();
  return (
    <nav aria-label={t('verify.stage.label')} data-testid="stage-tracker">
      <ol className="grid grid-cols-4 gap-1">
        {STAGES.map((stage, i) => {
          const state = states[stage];
          const on = stage === shown;
          const marker =
            state === 'done' ? <Check className="h-4 w-4" strokeWidth={3} aria-hidden="true" />
              : state === 'waiting' ? <Clock className="h-4 w-4" aria-hidden="true" />
                : state === 'fix' ? <AlertCircle className="h-4 w-4" aria-hidden="true" />
                  : <span className="text-sm font-bold">{i + 1}</span>;
          const tone =
            state === 'done' ? 'bg-accent-green text-text-inverse'
              : state === 'fix' ? 'bg-accent-red-tint text-danger'
                : state === 'waiting' ? 'bg-primary-light text-primary'
                  : 'border-2 border-primary text-primary';
          return (
            <li key={stage}>
              <button
                type="button"
                onClick={() => onPick(stage)}
                aria-current={on ? 'step' : undefined}
                className={`flex w-full flex-col items-center gap-1 rounded-input px-1 py-2 text-center ${on ? 'bg-primary-light' : ''}`}
                data-testid={`stage-${stage}`}
                data-state={state}
              >
                <span className={`flex h-8 w-8 items-center justify-center rounded-full ${tone}`}>{marker}</span>
                <span className="text-caption font-semibold leading-tight text-text-primary">{t(`verify.stage.${stage}`)}</span>
                <span className="text-[11px] leading-tight text-text-secondary">{t(`verify.stage.state.${state}`)}</span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

// ---------------------------------------------------------------- rows

function StepRow({
  rowRef,
  number,
  step,
  open,
  onToggle,
}: {
  rowRef: (el: HTMLDivElement | null) => void;
  number: number;
  step: Step;
  open: boolean;
  onToggle: () => void;
}) {
  const tone =
    step.state === 'fix'
      ? 'text-danger'
      : step.state === 'soon'
        ? 'text-accent-orange-strong'
        : step.state === 'done'
          ? 'text-accent-green-strong'
          : step.state === 'waiting'
            ? 'text-primary'
            : 'text-text-secondary';
  return (
    <div ref={rowRef} className="scroll-mt-4" data-testid={`step-${step.key}`} data-state={step.state}>
      <button type="button" className="flex w-full items-center gap-3 p-4 text-left" aria-expanded={open} onClick={onToggle}>
        <StepMarker number={number} state={step.state} />
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold text-text-primary">{step.title}</span>
          <span className={`mt-0.5 block text-caption ${tone}`}>{step.summaryNode ?? step.summary}</span>
        </span>
        <ChevronDown className={`h-5 w-5 shrink-0 text-text-secondary transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden="true" />
      </button>
      {open && <div className="px-4 pb-5">{step.body}</div>}
    </div>
  );
}

function StepMarker({ number, state }: { number: number; state: StepState }) {
  const base = 'flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-sm font-bold';
  switch (state) {
    case 'done':
      return (
        <span className={`${base} bg-accent-green text-text-inverse`}>
          <Check className="h-5 w-5" strokeWidth={3} aria-hidden="true" />
        </span>
      );
    case 'soon':
      return (
        <span className={`${base} bg-accent-orange-tint text-accent-orange-strong`}>
          <Clock className="h-5 w-5" aria-hidden="true" />
        </span>
      );
    case 'waiting':
      return (
        <span className={`${base} bg-primary-light text-primary`}>
          <Clock className="h-5 w-5" aria-hidden="true" />
        </span>
      );
    case 'fix':
      return (
        <span className={`${base} bg-accent-red-tint text-danger`}>
          <AlertCircle className="h-5 w-5" aria-hidden="true" />
        </span>
      );
    case 'locked':
      return (
        <span className={`${base} bg-background text-text-secondary`}>
          <Lock className="h-4 w-4" aria-hidden="true" />
        </span>
      );
    default:
      return <span className={`${base} border-2 border-primary text-primary`}>{number}</span>;
  }
}

/** What an open step says when there is nothing for her to send: why it matters, and where it stands. */
function StateNote({ state, why }: { state: StepState; why: string }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-2 text-sm text-text-secondary">
      <p>{why}</p>
      {state === 'waiting' && <p className="rounded-input bg-primary-light p-3 text-text-primary">{t('verify.waitingBody')}</p>}
      {state === 'locked' && <p className="rounded-input bg-background p-3">{t('verify.status.locked')}</p>}
    </div>
  );
}

// ---------------------------------------------------------------- bodies

function ConsentBody({ consent, onAgreed }: { consent: ConsentStatus | null; onAgreed: (c: ConsentStatus) => void }) {
  const { t } = useTranslation();
  const [full, setFull] = useState(false);
  const [agreeing, setAgreeing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (consent?.current) {
    return (
      <p className="text-sm text-text-secondary" data-testid="consent-step">
        {consent.acceptedAt ? t('verify.consent.agreedOn', { date: formatDay(consent.acceptedAt.slice(0, 10)) }) : t('verify.status.done')}
      </p>
    );
  }

  async function agree() {
    if (!consent) return;
    setAgreeing(true);
    setError(null);
    try {
      onAgreed(await verificationApi.acceptConsent(consent.currentVersion));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('docs.consentError'));
    } finally {
      setAgreeing(false);
    }
  }

  return (
    <div className="space-y-3" data-testid="consent-step">
      <p className="text-sm text-text-secondary">{t('verify.consent.intro')}</p>
      <ul className="space-y-2 text-sm text-text-primary">
        {['point1', 'point2', 'point3'].map((k) => (
          <li key={k} className="flex items-start gap-2">
            <ScrollText className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
            {t(`verify.consent.${k}`)}
          </li>
        ))}
      </ul>
      <button type="button" className="tap-target text-sm font-semibold text-primary" onClick={() => setFull(!full)} data-testid="consent-read">
        {full ? t('verify.consent.hideFull') : t('verify.consent.readFull')}
      </button>
      {full && (
        <div className="max-h-72 space-y-3 overflow-y-auto rounded-card bg-background p-3 text-caption leading-relaxed text-text-secondary" data-testid="consent-text">
          <p className="font-medium text-text-primary">{highlightPlaceholders(PARTNER_VERIFICATION_CONSENT.intro)}</p>
          {PARTNER_VERIFICATION_CONSENT.sections.map((s) => (
            <div key={s.heading} className="space-y-1">
              <p className="font-semibold text-text-primary">{s.heading}</p>
              {s.paragraphs.map((p, i) => (
                <p key={i}>{highlightPlaceholders(p)}</p>
              ))}
            </div>
          ))}
          <p>{PARTNER_VERIFICATION_CONSENT.effective}</p>
        </div>
      )}
      {error && <p className="text-sm text-danger">{error}</p>}
      <Button fullWidth disabled={agreeing || !consent} onClick={agree} data-testid="consent-agree">
        {agreeing ? t('common.saving') : t('verify.consent.agree')}
      </Button>
    </div>
  );
}

/** Her ID and the live selfie, together: one identity review. */
function IdBody({ onSent }: { onSent: () => void }) {
  const { t } = useTranslation();
  const [idFile, setIdFile] = useState<File | null>(null);
  /** What the photo check noticed about her ID and she sent anyway. */
  const [idWarnings, setIdWarnings] = useState<PhotoWarning[]>([]);
  /** An ID photo she is covering her number on, before it is kept. */
  const [masking, setMasking] = useState<File | null>(null);
  const [selfie, setSelfie] = useState<LiveSelfieResult | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!idFile || !selfie) return;
    setUploading(true);
    setError(null);
    try {
      await verificationApi.uploadDocuments(idFile, selfie, photoWarningsParam(idWarnings));
      onSent();
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'SELFIE_CHALLENGE_EXPIRED') setSelfie(null);
      setError(err instanceof ApiError ? err.message : t('verification.uploadError'));
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="space-y-5">
      <p className="text-sm text-text-secondary">{t('verify.id.why')}</p>

      <div className="space-y-2">
        <p className="text-sm font-semibold text-text-primary">{t('verify.id.stepA')}</p>
        <p className="text-caption text-text-secondary">{t('verify.id.stepAHint')}</p>
        {masking ? (
          <DocumentMasker
            file={masking}
            busy={uploading}
            onCancel={() => setMasking(null)}
            onDone={(masked) => {
              setIdFile(masked);
              setMasking(null);
            }}
          />
        ) : (
          <>
            <PhotoPicker
              file={idFile}
              disabled={uploading}
              testId="id-photo"
              expectFace
              onPick={(file, warnings) => {
                setIdWarnings(warnings);
                if (file.type.startsWith('image/')) setMasking(file);
                else setIdFile(file);
              }}
            />
            <p className="text-caption text-text-secondary" data-testid="mask-invite">{t('verify.id.maskTip')}</p>
          </>
        )}
      </div>

      <div className="space-y-2">
        <p className="flex items-center gap-2 text-sm font-semibold text-text-primary">
          <Camera className="h-4 w-4 text-primary" aria-hidden="true" />
          {t('verify.id.stepB')}
        </p>
        <p className="text-caption text-text-secondary">{t('verify.id.stepBHint')}</p>
        <LiveSelfieCapture
          requestChallenge={() => verificationApi.selfieChallenge()}
          onCaptured={setSelfie}
          onReset={() => setSelfie(null)}
          captured={selfie}
          busy={uploading}
          findFace={findFaceIn}
        />
      </div>

      {error && <p className="text-sm text-danger" role="alert">{error}</p>}
      <Button fullWidth disabled={uploading || !idFile || !selfie} onClick={submit} data-testid="verification-submit">
        {uploading ? t('common.uploading') : !idFile ? t('verify.id.needId') : !selfie ? t('verify.id.needSelfie') : t('verify.id.send')}
      </Button>
    </div>
  );
}

/** Her profile photo, added right here rather than on another screen she has to find. */
function PhotoBody({ profile, onProfile }: { profile: DriverProfileSummary | null; onProfile: (p: DriverProfileSummary) => void }) {
  const { t } = useTranslation();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function picked(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      onProfile(await usersApi.uploadMyPhoto(await shrinkPhoto(file)));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : err instanceof Error ? err.message : t('verify.photo.error'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-3">
      <input ref={input} type="file" accept="image/*" capture="user" className="hidden" onChange={picked} data-testid="profile-photo-input" />
      <div className="flex items-center gap-3">
        <Avatar name={profile?.name} url={profile?.profilePhotoUrl} size="lg" />
        <p className="flex-1 text-sm text-text-secondary">{t('verify.photo.why')}</p>
      </div>
      {error && <p className="text-sm text-danger" role="alert">{error}</p>}
      <Button
        fullWidth
        variant={profile?.hasProfilePhoto ? 'secondary' : 'primary'}
        icon={<UserRound className="h-4 w-4" />}
        disabled={busy}
        onClick={() => input.current?.click()}
        data-testid="profile-photo-add"
      >
        {busy ? t('common.uploading') : profile?.hasProfilePhoto ? t('verify.photo.change') : t('verify.photo.add')}
      </Button>
    </div>
  );
}

/** One document step opened: why it is needed, then the form - or, while it is with us, that nothing is needed. */
function DocumentBody({
  type,
  state,
  stepState,
  why,
  extra,
  profilePlate,
  onSent,
}: {
  type: PartnerDocumentType;
  state: DocumentState | undefined;
  stepState: StepState;
  why: string;
  extra?: ReactNode;
  profilePlate?: string | null;
  onSent: () => void;
}) {
  const { t } = useTranslation();
  const [renewing, setRenewing] = useState(false);
  const formOpen = stepState === 'todo' || stepState === 'fix' || stepState === 'soon' || renewing;

  return (
    <div className="space-y-4">
      <p className="flex items-start gap-2 text-sm text-text-secondary">
        <span className="mt-0.5 shrink-0 text-primary">{DOC_ICONS[type]}</span>
        {why}
      </p>
      {state?.insuranceUseType && state.insuranceUseType !== 'COMMERCIAL' && stepState === 'fix' && (
        <p className="rounded-input bg-accent-red-tint p-3 text-sm text-text-primary">{t('verify.doc.privateWarning')}</p>
      )}
      {extra}
      {stepState === 'waiting' && <p className="rounded-input bg-primary-light p-3 text-sm text-text-primary">{t('verify.waitingBody')}</p>}
      {stepState === 'locked' && <p className="rounded-input bg-background p-3 text-sm text-text-secondary">{t('verify.status.locked')}</p>}
      {formOpen ? (
        <DocumentForm type={type} state={state} profilePlate={profilePlate} onSent={onSent} onCancel={renewing ? () => setRenewing(false) : undefined} />
      ) : (
        stepState === 'done' &&
        !state?.renewalUnderReview && (
          <Button size="md" variant="secondary" onClick={() => setRenewing(true)} data-testid={`doc-open-${type}`}>
            {t('docs.uploadRenewal')}
          </Button>
        )
      )}
    </div>
  );
}

/** For a partner without a police certificate yet: where to get one, and that the other steps need not wait. */
function PoliceHowTo() {
  const { t } = useTranslation();
  const [steps, setSteps] = useState(false);
  return (
    <div className="space-y-2 rounded-card border border-border p-3 text-sm" data-testid="police-steps">
      <p className="font-semibold text-text-primary">{t('verify.police.noneYet')}</p>
      <p className="text-text-secondary">{t('verify.police.applyBody')}</p>
      <a
        href={TS_POLICE_PORTAL}
        target="_blank"
        rel="noreferrer"
        className="inline-flex items-center gap-1 font-semibold text-primary"
        data-testid="police-portal-link"
      >
        {t('verify.police.openPortal')} <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
      </a>
      <button type="button" className="block text-caption font-medium text-text-secondary underline underline-offset-4" onClick={() => setSteps(!steps)}>
        {t('verify.police.howTo')}
      </button>
      {steps && (
        <ol className="list-decimal space-y-1 pl-5 text-caption text-text-secondary">
          <li>{t('verify.police.step1')}</li>
          <li>{t('verify.police.step2')}</li>
          <li>{t('verify.police.step3')}</li>
        </ol>
      )}
      <p className="text-caption text-text-secondary">{t('verify.police.sheoutAdds')}</p>
      <p className="pt-1 font-semibold text-text-primary">{t('verify.police.haveIt')}</p>
    </div>
  );
}

/** PAN goes to her profile, not to a review: optional, for tax on payouts. */
function PanRow({
  profile,
  onProfile,
  open,
  onToggle,
}: {
  profile: DriverProfileSummary | null;
  onProfile: (p: DriverProfileSummary) => void;
  open: boolean;
  onToggle: () => void;
}) {
  const { t } = useTranslation();
  const [pan, setPan] = useState(profile?.panNumber ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  useEffect(() => setPan(profile?.panNumber ?? ''), [profile?.panNumber]);

  const onFile = Boolean(profile?.panNumber);
  const changed = pan.trim().toUpperCase() !== (profile?.panNumber ?? '');
  const usable = pan.trim() === '' ? onFile : /^[A-Z]{5}[0-9]{4}[A-Z]$/.test(pan.trim().toUpperCase());

  async function save() {
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await usersApi.updateMyPan(pan.trim());
      onProfile(updated);
      setPan(updated.panNumber ?? '');
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('verification.panSaveError'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div data-testid="step-pan">
      <button type="button" className="flex w-full items-center gap-3 p-4 text-left" aria-expanded={open} onClick={onToggle}>
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-background text-text-secondary">
          <IdCard className="h-4 w-4" aria-hidden="true" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold text-text-primary">{t('verify.pan.title')}</span>
          <span className={`mt-0.5 block text-caption ${onFile ? 'text-accent-green-strong' : 'text-text-secondary'}`}>
            {onFile ? t('verify.pan.summaryOn') : t('verify.pan.summaryOff')}
          </span>
        </span>
        <ChevronDown className={`h-5 w-5 shrink-0 text-text-secondary transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden="true" />
      </button>
      {open && (
        <div className="space-y-3 px-4 pb-5">
          <p className="text-sm text-text-secondary">{t('verification.panBody')}</p>
          <TextField
            label="PAN"
            name="panNumber"
            value={pan}
            maxLength={10}
            autoCapitalize="characters"
            autoComplete="off"
            placeholder="ABCDE1234F"
            onChange={(e) => {
              setPan(e.target.value.toUpperCase());
              setSaved(false);
              setError(null);
            }}
            error={error ?? undefined}
          />
          {saved && !changed && <p className="text-caption text-accent-green-strong">{t('common.saved')}</p>}
          <Button fullWidth variant="secondary" disabled={saving || !changed || !usable} onClick={save}>
            {saving
              ? t('common.saving')
              : pan.trim() === '' && onFile
                ? t('verification.removePan')
                : onFile
                  ? t('verification.updatePan')
                  : t('verification.savePan')}
          </Button>
        </div>
      )}
    </div>
  );
}
