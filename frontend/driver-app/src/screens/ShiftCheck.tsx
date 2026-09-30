import { Clock, Lightbulb, ScanFace, ShieldCheck } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  ContactSupportButton,
  IconCircle,
  LiveSelfieCapture,
  SkeletonCard,
  Stepper,
  SuccessCheck,
  ThinkingIndicator,
  TopHeader,
} from '@sheout/design-system';
import type { LiveSelfieResult } from '@sheout/design-system';
import { useTranslation } from '@sheout/design-system';
import { ApiError, supportApi, usersApi, verificationApi } from '../api/client';
import type { ShiftCheckChallenge, ShiftCheckStatus } from '../api/client';
import type { DriverProfileSummary } from '../api/types';
import { HelmetCapture } from '../components/HelmetCapture';
import { compareFaces, preloadFaceModels, type FaceComparison } from '../lib/faceMatch';
import { readPositionOnce } from '../lib/LocationBroadcastContext';
import { apiErrorText } from '../lib/apiErrors';

type Step = 'selfie' | 'matching' | 'helmet' | 'sending' | 'passed' | 'retry' | 'review';

function timeOf(iso: string | null): string {
  return iso ? new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }) : '';
}

/**
 * Start of shift: a selfie, and on a two-wheeler a photo with her helmet on.
 * <p>
 * What Uber calls a Real-Time ID Check and Rapido a selfie before going
 * online. Her ID was checked once, by a person; this is how a rider knows
 * the woman on the bike today is that same woman, and it is what the
 * rider's screen means when it says "Face verified today".
 * <p>
 * THREE SCREENS, NO MORE. Selfie; helmet (bike only); done - and "done" is
 * a Go Online button, because going online is what she came here to do.
 * <p>
 * When it does not match, she is told plainly what usually causes it (light,
 * sunglasses, a mask) and how many tries she has before somebody at SheOut
 * looks. Held for review, she is told that too, with the support number -
 * not left tapping a button that keeps failing.
 */
export function ShiftCheck() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const routeState = useLocation().state as { goOnline?: boolean } | null;
  const [status, setStatus] = useState<ShiftCheckStatus | null>(null);
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [step, setStep] = useState<Step>('selfie');
  const [error, setError] = useState<string | null>(null);
  const [goingOnline, setGoingOnline] = useState(false);
  const [supportPhone, setSupportPhone] = useState<string | null>(null);
  /** The challenge behind the selfie on screen - its reference photo is what we compare with. */
  const challenge = useRef<ShiftCheckChallenge | null>(null);
  const live = useRef<LiveSelfieResult | null>(null);
  const comparison = useRef<FaceComparison>({ outcome: 'UNAVAILABLE' });

  const needsHelmet = profile ? profile.vehicleType == null || profile.vehicleType === 'BIKE' : true;
  const steps = needsHelmet
    ? [t('shiftCheck.step.selfie'), t('shiftCheck.step.helmet'), t('shiftCheck.step.done')]
    : [t('shiftCheck.step.selfie'), t('shiftCheck.step.done')];
  const stepIndex = step === 'passed' ? steps.length - 1 : step === 'helmet' || (step === 'sending' && needsHelmet) ? 1 : 0;

  useEffect(() => {
    preloadFaceModels();
    usersApi.getMyProfile().then(setProfile).catch(() => undefined);
    supportApi.getContact().then((c) => setSupportPhone(c.phoneNumber || null)).catch(() => undefined);
    verificationApi
      .shiftCheckStatus()
      .then((s) => {
        setStatus(s);
        if (s.underReview) setStep('review');
        else if (s.valid && (s.helmetPhotoOnFile || !routeState?.goOnline)) setStep('passed');
      })
      .catch((err) => setError(apiErrorText(err, 'shiftCheck.loadError')));
    // Once, on arrival.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function requestChallenge() {
    const next = await verificationApi.shiftCheckChallenge();
    challenge.current = next;
    return next;
  }

  async function onSelfie(result: LiveSelfieResult) {
    live.current = result;
    setError(null);
    setStep('matching');
    const compared = await compareFaces(challenge.current?.referenceSelfieUrl ?? null, result.selfie);
    comparison.current = compared;
    if (compared.outcome === 'NO_FACE') {
      setError(t('shiftCheck.noFace'));
      setStep('selfie');
      return;
    }
    // The helmet photo is taken whatever the faces scored. A check that did
    // not match may be cleared by an operator later, and it has to be a
    // whole check when it is - or she would be sent through it all again.
    if (needsHelmet) setStep('helmet');
    else await submit(null);
  }

  async function submit(helmet: File | null) {
    if (!live.current) return;
    setStep('sending');
    const compared = comparison.current;
    try {
      const result = await verificationApi.submitShiftCheck({
        live: live.current,
        helmet,
        faceDistance: compared.outcome === 'COMPARED' ? compared.distance : null,
        faceOutcome: compared.outcome === 'COMPARED' ? null : compared.outcome,
      });
      setStatus(result);
      setStep(result.underReview ? 'review' : result.valid ? 'passed' : 'retry');
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'SHIFT_CHECK_UNDER_REVIEW') {
        setStep('review');
        return;
      }
      setError(apiErrorText(err, 'shiftCheck.sendError'));
      setStep('selfie');
    } finally {
      live.current = null;
    }
  }

  async function goOnline() {
    setGoingOnline(true);
    setError(null);
    try {
      const here = await readPositionOnce(null);
      if (!here) {
        setError(t('home.needLocation'));
        return;
      }
      await usersApi.setOnlineStatus('ONLINE', here);
      navigate('/home', { replace: true });
    } catch (err) {
      setError(apiErrorText(err, 'home.statusError'));
    } finally {
      setGoingOnline(false);
    }
  }

  return (
    <div className="space-y-5" data-testid="shift-check">
      <TopHeader variant="back" title={t('shiftCheck.title')} onBack={() => navigate('/home')} />

      {!status && !error && <SkeletonCard lines={4} label={t('shiftCheck.loading')} />}

      {status && step !== 'review' && <Stepper steps={steps} current={stepIndex} />}

      {status && step === 'selfie' && (
        <Card className="space-y-4">
          <div className="flex items-start gap-3">
            <IconCircle tone="soft" icon={<ScanFace />} />
            <div className="min-w-0 flex-1">
              <p className="font-heading text-card-title text-text-primary">{t('shiftCheck.selfieTitle')}</p>
              <p className="mt-1 text-sm text-text-secondary">{t('shiftCheck.selfieBody')}</p>
            </div>
          </div>
          <ul className="space-y-1.5 rounded-input bg-background px-3 py-3 text-sm text-text-primary">
            <li className="flex items-center gap-2"><Lightbulb className="h-4 w-4 shrink-0 text-accent-orange" aria-hidden="true" />{t('shiftCheck.tip.light')}</li>
            <li className="flex items-center gap-2"><Lightbulb className="h-4 w-4 shrink-0 text-accent-orange" aria-hidden="true" />{t('shiftCheck.tip.face')}</li>
            <li className="flex items-center gap-2"><Clock className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />{t('shiftCheck.tip.time')}</li>
          </ul>
          {error && (
            <p className="rounded-input bg-danger/10 px-3 py-2 text-sm font-medium text-danger" role="alert" data-testid="shift-check-error">
              {error}
            </p>
          )}
          <LiveSelfieCapture
            requestChallenge={requestChallenge}
            onCaptured={onSelfie}
            why={t('shiftCheck.why')}
            startLabel={t('shiftCheck.start')}
          />
        </Card>
      )}

      {(step === 'matching' || step === 'sending') && (
        <Card className="flex flex-col items-center gap-3 py-8 text-center" data-testid="shift-check-matching">
          <ThinkingIndicator mode="searching" size={64} label={t('shiftCheck.matching')} />
          <p className="font-heading text-card-title text-text-primary">
            {step === 'matching' ? t('shiftCheck.matching') : t('shiftCheck.sending')}
          </p>
          <p className="text-sm text-text-secondary">{t('shiftCheck.matchingBody')}</p>
        </Card>
      )}

      {step === 'helmet' && (
        <Card>
          <HelmetCapture onCaptured={(photo) => submit(photo)} />
        </Card>
      )}

      {step === 'retry' && status && (
        <Card tone="warning" className="space-y-4" data-testid="shift-check-retry">
          <p className="font-heading text-card-title text-text-primary">{t('shiftCheck.noMatchTitle')}</p>
          <p className="text-sm text-text-secondary">{t('shiftCheck.noMatchBody')}</p>
          <ul className="space-y-1.5 text-sm text-text-primary">
            <li>• {t('shiftCheck.tip.light')}</li>
            <li>• {t('shiftCheck.tip.face')}</li>
            <li>• {t('shiftCheck.tip.still')}</li>
          </ul>
          <p className="text-sm font-semibold text-text-primary">
            {t('shiftCheck.triesLeft', { count: status.missesBeforeReview })}
          </p>
          <Button fullWidth onClick={() => { setError(null); setStep('selfie'); }} data-testid="shift-check-again">
            {t('shiftCheck.tryAgain')}
          </Button>
        </Card>
      )}

      {step === 'review' && (
        <Card className="space-y-4" data-testid="shift-check-review">
          <div className="flex items-start gap-3">
            <IconCircle tone="soft" color="orange" icon={<ShieldCheck />} />
            <div className="min-w-0 flex-1">
              <p className="font-heading text-card-title text-text-primary">{t('shiftCheck.reviewTitle')}</p>
              <p className="mt-1 text-sm text-text-secondary">{t('shiftCheck.reviewBody')}</p>
            </div>
          </div>
          <ContactSupportButton phoneNumber={supportPhone} />
          <Button fullWidth variant="secondary" onClick={() => navigate('/home')}>{t('shiftCheck.backHome')}</Button>
        </Card>
      )}

      {step === 'passed' && status && (
        <Card className="flex flex-col items-center gap-3 py-7 text-center" data-testid="shift-check-passed">
          <SuccessCheck size={72} label={t('shiftCheck.passedTitle')} />
          <p className="font-heading text-title text-text-primary">
            {profile?.name ? t('shiftCheck.passedTitleName', { name: profile.name.trim().split(/\s+/)[0] }) : t('shiftCheck.passedTitle')}
          </p>
          <p className="text-sm text-text-secondary">
            {status.validUntil ? t('shiftCheck.passedUntil', { time: timeOf(status.validUntil) }) : t('shiftCheck.passedBody')}
          </p>
          {status.faceMatched && (
            <p className="rounded-full bg-accent-green/10 px-3 py-1 text-sm font-semibold text-accent-green-strong">
              {t('shiftCheck.riderSees')}
            </p>
          )}
          <Button fullWidth size="lg" variant="success" disabled={goingOnline} onClick={goOnline} data-testid="shift-check-go-online">
            {goingOnline ? t('shiftCheck.goingOnline') : t('shiftCheck.goOnline')}
          </Button>
          <Button fullWidth variant="secondary" onClick={() => navigate('/home')}>{t('shiftCheck.notYet')}</Button>
        </Card>
      )}

      {error && (step !== 'selfie' || !status) && <p className="text-center text-sm text-danger" role="alert">{error}</p>}
    </div>
  );
}
