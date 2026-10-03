import { Camera, Car, CheckCircle2, Clock, ExternalLink, FileCheck2, FileText, Gauge, IdCard, ScrollText, ShieldCheck, Check, Upload, Wrench } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGoBack } from '../lib/useGoBack';
import {
  Button,
  Card,
  DocumentMasker,
  IconCircle,
  LiveSelfieCapture,
  PARTNER_VERIFICATION_CONSENT,
  SkeletonCard,
  StatusBadge,
  TextField,
  TopHeader,
  highlightPlaceholders,
  verificationStatusLabel,
} from '@sheout/design-system';
import type { LiveSelfieResult } from '@sheout/design-system';
import type { StatusTone } from '@sheout/design-system';
import { ApiError, usersApi, verificationApi } from '../api/client';
import type { ConsentStatus, DriverProfileSummary, PartnerReadiness, VerificationStatus, VerificationSummary } from '../api/types';
import { useTranslation } from '@sheout/design-system';
import { DocumentStep } from '../components/DocumentStep';
import { blockerText } from '../lib/readinessText';
import { formatDay } from '../lib/documentDates';

function statusTone(status: VerificationStatus | null): StatusTone {
  switch (status) {
    case 'VERIFIED':
      return 'success';
    case 'REJECTED':
      return 'danger';
    case 'UNDER_REVIEW':
      return 'primary';
    default:
      return 'warning';
  }
}

/** Telangana Police's Police Verification Certificate portal (i-Verify). */
const TS_POLICE_PORTAL = 'https://pvc.tspolice.gov.in/';

/**
 * REAL: her checklist, in the order she does it - consent, ID and selfie,
 * licence, vehicle RC, vehicle insurance, PUC (and fitness for an auto or
 * cab), police certificate. Each step says where it stands, why if it was
 * turned down, when it runs out, and lets her send it (again).
 * <p>
 * Where she stands comes from GET /users/driver/me/readiness - the same
 * answer going online is decided on, so this screen cannot tell her she is
 * ready while the server refuses her. The ID and selfie still go up through
 * the identity submission; every other document has its own upload.
 * <p>
 * Consent first: nothing can be sent until she has agreed to SheOut
 * verifying her identity, documents and police record, in the wording in
 * force (PARTNER_VERIFICATION_CONSENT, still marked for the lawyer).
 */
export function Verification() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const goBack = useGoBack('/home');
  const idInputRef = useRef<HTMLInputElement>(null);
  const [idFile, setIdFile] = useState<File | null>(null);
  /** An ID photo she is covering her number on, before it is kept. */
  const [masking, setMasking] = useState<File | null>(null);
  const [selfie, setSelfie] = useState<LiveSelfieResult | null>(null);
  const [summary, setSummary] = useState<VerificationSummary | null>(null);
  const [readiness, setReadiness] = useState<PartnerReadiness | null>(null);
  const [consent, setConsent] = useState<ConsentStatus | null>(null);
  const [readingConsent, setReadingConsent] = useState(false);
  const [agreeing, setAgreeing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  const [pan, setPan] = useState('');
  const [savingPan, setSavingPan] = useState(false);
  const [panError, setPanError] = useState<string | null>(null);
  const [panSaved, setPanSaved] = useState(false);

  function pickFile(e: React.ChangeEvent<HTMLInputElement>, set: (f: File | null) => void) {
    const file = e.target.files?.[0] ?? null;
    e.target.value = '';
    if (file) set(file);
  }

  function load() {
    verificationApi
      .getMyStatus()
      .then(setSummary)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('verification.loadError')));
    usersApi.getReadiness().then(setReadiness).catch(() => {
      // The checklist still renders from the summary; only the per-document states are missing.
    });
    verificationApi.getConsent().then(setConsent).catch(() => {});
  }

  useEffect(load, []);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then((p) => {
        setProfile(p);
        setPan(p.panNumber ?? '');
      })
      .catch(() => {});
  }, []);

  async function agree() {
    if (!consent) return;
    setAgreeing(true);
    setError(null);
    try {
      setConsent(await verificationApi.acceptConsent(consent.currentVersion));
      setReadingConsent(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('docs.consentError'));
      verificationApi.getConsent().then(setConsent).catch(() => {});
    } finally {
      setAgreeing(false);
    }
  }

  /** Her ID and the live selfie, together: one identity review. */
  async function handleSubmit() {
    if (!idFile || !selfie) return;
    setUploading(true);
    setError(null);
    try {
      const updated = await verificationApi.uploadDocuments(idFile, selfie);
      setSummary(updated);
      setIdFile(null);
      setSelfie(null);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.body?.error === 'SELFIE_CHALLENGE_EXPIRED') setSelfie(null);
      setError(err instanceof ApiError ? err.message : t('verification.uploadError'));
    } finally {
      setUploading(false);
    }
  }

  /** The PAN goes to the profile, not to a review - see the header comment of the earlier version in git. */
  async function savePan() {
    setSavingPan(true);
    setPanError(null);
    setPanSaved(false);
    try {
      const updated = await usersApi.updateMyPan(pan.trim());
      setProfile(updated);
      setPan(updated.panNumber ?? '');
      setPanSaved(true);
    } catch (err) {
      setPanError(err instanceof ApiError ? err.message : t('verification.panSaveError'));
    } finally {
      setSavingPan(false);
    }
  }

  const panOnFile = Boolean(profile?.panNumber);
  const panChanged = pan.trim().toUpperCase() !== (profile?.panNumber ?? '');
  const panUsable = pan.trim() === '' ? panOnFile : /^[A-Z]{5}[0-9]{4}[A-Z]$/.test(pan.trim().toUpperCase());

  const consented = Boolean(consent?.current);
  const hasPhoto = Boolean(profile?.hasProfilePhoto);
  const ready = Boolean(readiness?.ready) && hasPhoto;
  const firstBlocker = readiness?.blockers[0];
  const canUploadId = Boolean(summary) && consented
    && summary!.genderVerificationStatus !== 'VERIFIED' && summary!.genderVerificationStatus !== 'UNDER_REVIEW';
  const docState = (type: string) => readiness?.documents.find((d) => d.type === type);
  const vehicleSteps = (readiness?.documents ?? []).filter((d) => d.required);
  const policeDue = readiness?.blockers.concat(readiness.warnings).find((b) => b.code === 'POLICE_REVERIFY_DUE');
  const ICONS: Record<string, JSX.Element> = {
    DRIVING_LICENCE: <IconCircle size="sm" tone="soft" icon={<IdCard />} />,
    VEHICLE_RC: <IconCircle size="sm" tone="soft" color="orange" icon={<Car />} />,
    VEHICLE_INSURANCE: <IconCircle size="sm" tone="soft" color="green" icon={<ShieldCheck />} />,
    PUC: <IconCircle size="sm" tone="soft" icon={<Gauge />} />,
    FITNESS_CERTIFICATE: <IconCircle size="sm" tone="soft" icon={<Wrench />} />,
  };

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('verification.title')} onBack={goBack} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!summary && !error && <SkeletonCard lines={3} label={t('verification.loading')} />}

      {summary && (
        <>
          {/* Where she stands, from the same answer going online uses: the
              first thing to do, in her words, or that she is ready. */}
          <Card tone={ready ? 'success' : 'warning'} className="flex items-start gap-3" data-testid="readiness-card">
            <IconCircle color={ready ? 'green' : 'orange'} tone="soft" icon={ready ? <ShieldCheck /> : <Clock />} />
            <div className="flex-1">
              <p className="font-heading text-card-title text-text-primary">
                {ready ? t('verification.readyTitle') : readiness?.ready && !hasPhoto ? t('verification.oneMoreTitle') : t('docs.notReadyTitle')}
              </p>
              <p className="mt-1 text-xs text-text-secondary">
                {ready
                  ? t('docs.readyBody')
                  : readiness?.ready && !hasPhoto
                    ? t('verification.oneMoreBody')
                    : firstBlocker
                      ? blockerText(t, firstBlocker)
                      : t('verification.inProgressBody')}
              </p>
              {readiness?.ready && !hasPhoto && (
                <Button size="md" className="mt-3" onClick={() => navigate('/profile')}>
                  {t('verification.addPhoto')}
                </Button>
              )}
              {(readiness?.warnings ?? []).map((w, i) => (
                <p key={i} className="mt-2 text-xs text-accent-orange-strong">{blockerText(t, w)}</p>
              ))}
            </div>
          </Card>

          <h2 className="font-heading text-section text-text-primary">{t('docs.checklistTitle')}</h2>

          <Card className="divide-y divide-border p-0">
            {/* 1. Consent */}
            <div className="space-y-2 p-4" data-testid="consent-step">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <IconCircle size="sm" tone="soft" icon={<ScrollText />} />
                  <span className="text-sm font-medium text-text-primary">{t('docs.consentStep')}</span>
                </div>
                <StatusBadge tone={consented ? 'success' : 'warning'}>
                  {consented ? t('docs.consentGiven') : consent?.acceptedVersion ? t('docs.consentUpdated') : t('docs.consentNeeded')}
                </StatusBadge>
              </div>
              {consented ? (
                consent?.acceptedAt && (
                  <p className="text-xs text-text-secondary">{t('docs.consentGivenOn', { date: formatDay(consent.acceptedAt.slice(0, 10)) })}</p>
                )
              ) : (
                <>
                  <p className="text-xs text-text-secondary">{t('docs.consentIntro')}</p>
                  {readingConsent ? (
                    <div className="space-y-3 rounded-card bg-background p-3 text-xs leading-relaxed text-text-secondary" data-testid="consent-text">
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
                      <Button fullWidth disabled={agreeing || !consent} onClick={agree} data-testid="consent-agree">
                        {agreeing ? t('common.saving') : t('docs.consentAgree')}
                      </Button>
                    </div>
                  ) : (
                    <Button size="md" onClick={() => setReadingConsent(true)} data-testid="consent-read">
                      {t('docs.consentRead')}
                    </Button>
                  )}
                </>
              )}
            </div>

            {/* 2. ID and live selfie - one identity review */}
            <div className="space-y-2 p-4">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <IconCircle size="sm" tone="soft" icon={<CheckCircle2 />} />
                  <span className="text-sm font-medium text-text-primary">{t('docs.idStep')}</span>
                </div>
                <StatusBadge tone={statusTone(summary.genderVerificationStatus)}>{verificationStatusLabel(summary.genderVerificationStatus)}</StatusBadge>
              </div>
              {summary.genderVerificationStatus === 'REJECTED' && summary.rejectionReason && (
                <p className="text-xs text-danger">{t('docs.rejectedBecause', { reason: summary.rejectionReason })}</p>
              )}
              <p className="text-xs text-text-secondary">{t('verification.yourIdBody')}</p>
              {canUploadId && (
                <div className="space-y-2">
                  <p className="rounded-card bg-background p-3 text-xs leading-relaxed text-text-secondary" data-testid="mask-invite">
                    {t('verification.maskInvite')}
                  </p>
                  <input
                    ref={idInputRef}
                    type="file"
                    accept="image/*,.pdf"
                    className="hidden"
                    onChange={(e) => pickFile(e, (file) => {
                      if (file && file.type.startsWith('image/')) setMasking(file);
                      else setIdFile(file);
                    })}
                  />
                  {masking && (
                    <DocumentMasker
                      file={masking}
                      busy={uploading}
                      onCancel={() => setMasking(null)}
                      onDone={(masked) => {
                        setIdFile(masked);
                        setMasking(null);
                      }}
                    />
                  )}
                  <Button
                    fullWidth
                    variant="secondary"
                    icon={idFile ? <Check className="h-4 w-4" /> : <Upload className="h-4 w-4" />}
                    disabled={uploading}
                    onClick={() => idInputRef.current?.click()}
                  >
                    {idFile ? idFile.name : t('verification.chooseId')}
                  </Button>
                  <div className="flex items-center gap-2">
                    <IconCircle size="sm" tone="soft" icon={<Camera />} />
                    <p className="text-sm font-semibold text-text-primary">{t('verification.selfieTitle')}</p>
                  </div>
                  <LiveSelfieCapture
                    requestChallenge={() => verificationApi.selfieChallenge()}
                    onCaptured={setSelfie}
                    onReset={() => setSelfie(null)}
                    captured={selfie}
                    busy={uploading}
                  />
                  <Button fullWidth disabled={uploading || !idFile || !selfie} onClick={handleSubmit} data-testid="verification-submit">
                    {uploading
                      ? t('common.uploading')
                      : !idFile
                        ? t('verification.chooseIdToContinue')
                        : !selfie
                          ? t('verification.needSelfie')
                          : summary.documentSubmitted
                            ? t('docs.resubmitId')
                            : t('verification.submit')}
                  </Button>
                </div>
              )}
              {!consented && summary.genderVerificationStatus !== 'VERIFIED' && (
                <p className="text-xs text-text-secondary">{t('docs.needConsentFirst')}</p>
              )}
            </div>

            {/* 3-6. Licence, RC, insurance, PUC (and fitness) - what her vehicle needs */}
            {vehicleSteps.map((state) => (
              <DocumentStep
                key={state.type}
                type={state.type}
                state={state}
                icon={ICONS[state.type] ?? <IconCircle size="sm" tone="soft" icon={<FileText />} />}
                disabled={!consented}
                onSent={load}
              />
            ))}

            {/* 7. Police certificate - she applies on Telangana Police's portal and uploads it */}
            <DocumentStep
              type="POLICE_CERTIFICATE"
              state={docState('POLICE_CERTIFICATE')}
              icon={<IconCircle size="sm" tone="soft" icon={<FileCheck2 />} />}
              disabled={!consented}
              onSent={load}
              extra={
                <div className="space-y-2 text-xs text-text-secondary" data-testid="police-steps">
                  <div className="flex items-center justify-between gap-2">
                    <span>{t('docs.police.check')}</span>
                    <StatusBadge tone={statusTone(summary.policeVerificationStatus)}>
                      {summary.policeVerificationStatus === 'VERIFIED'
                        ? t('docs.police.verified')
                        : summary.policeVerificationStatus === 'REJECTED'
                          ? t('docs.police.rejected')
                          : docState('POLICE_CERTIFICATE')?.status && docState('POLICE_CERTIFICATE')?.status !== 'REJECTED'
                            ? t('docs.police.beingRecorded')
                            : t('docs.police.pending')}
                    </StatusBadge>
                  </div>
                  {policeDue?.date && <p className="text-accent-orange-strong">{t('docs.police.dueOn', { date: formatDay(policeDue.date) })}</p>}
                  <p>{t('docs.police.intro')}</p>
                  <ol className="list-decimal space-y-1 pl-4">
                    <li>{t('docs.police.step1')}</li>
                    <li>{t('docs.police.step2')}</li>
                    <li>{t('docs.police.step3')}</li>
                  </ol>
                  <a
                    href={TS_POLICE_PORTAL}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 font-medium text-primary"
                    data-testid="police-portal-link"
                  >
                    {t('docs.police.openPortal')} <ExternalLink className="h-3.5 w-3.5" />
                  </a>
                  <p>{t('docs.police.orSheOut')}</p>
                </div>
              }
            />
          </Card>

          {/* PAN, and deliberately nothing to do with the review above. It
              is asked for here because this is the screen where she is
              already dealing with paperwork. Optional; nothing waits on it. */}
          <Card className="space-y-3">
            <div className="flex items-start gap-3">
              <IconCircle tone="soft" color="green" icon={<IdCard />} />
              <div>
                <p className="font-heading text-card-title text-text-primary">{t('verification.panTitle')}</p>
                <p className="mt-1 text-xs text-text-secondary">{t('verification.panBody')}</p>
              </div>
            </div>
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
                setPanSaved(false);
                setPanError(null);
              }}
              error={panError ?? undefined}
            />
            {panSaved && !panChanged && <p className="text-xs text-accent-green-strong">{t('common.saved')}</p>}
            <Button fullWidth variant="secondary" disabled={savingPan || !panChanged || !panUsable} onClick={savePan}>
              {savingPan
                ? t('common.saving')
                : pan.trim() === ''
                  ? t('verification.removePan')
                  : panOnFile
                    ? t('verification.updatePan')
                    : t('verification.savePan')}
            </Button>
          </Card>
        </>
      )}
    </div>
  );
}
