import { Camera, Car, CheckCircle2, Clock, FileText, FileWarning, IdCard, ShieldCheck, Check, Upload } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, DocumentMasker, IconCircle, LiveSelfieCapture, SkeletonCard, StatusBadge, TextField, TopHeader, verificationStatusLabel } from '@sheout/design-system';
import type { LiveSelfieResult } from '@sheout/design-system';
import type { StatusTone } from '@sheout/design-system';
import { ApiError, usersApi, verificationApi } from '../api/client';
import type { DriverProfileSummary, VerificationStatus, VerificationSummary } from '../api/types';
import { useTranslation } from '@sheout/design-system';

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

function statusLabel(status: VerificationStatus | null): string {
  return verificationStatusLabel(status);
}

/**
 * REAL: status fetched from GET /api/v1/driver-verification/me, document
 * upload posts to POST /api/v1/driver-verification/documents (multipart).
 * <p>
 * Two documents now, submitted together: the identity document and the
 * vehicle's registration certificate. They go in one request because they
 * are evidence for one decision - an operator reads the ID to establish who
 * she is, and the RC to check the registration number she typed matches the
 * vehicle she actually owns. The second slot did not exist before, which is
 * why this screen used to have a single upload button.
 * <p>
 * STILL A GAP: police verification has no driver-facing submission step at
 * all. It is admin-only, so the row explaining it is not a real action and
 * does not pretend to be.
 */
export function Verification() {
  const { t } = useTranslation();
  const [explaining, setExplaining] = useState(false);
  const navigate = useNavigate();
  const idInputRef = useRef<HTMLInputElement>(null);
  const rcInputRef = useRef<HTMLInputElement>(null);
  const [idFile, setIdFile] = useState<File | null>(null);
  const [rcFile, setRcFile] = useState<File | null>(null);
  /** An ID photo she is covering her number on, before it is kept. */
  const [masking, setMasking] = useState<File | null>(null);
  /** The live selfie, required with the documents. */
  const [selfie, setSelfie] = useState<LiveSelfieResult | null>(null);
  const [summary, setSummary] = useState<VerificationSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  // Going online needs a photo as well as both checks, so this screen has
  // to know about it to tell the truth about what is still outstanding.
  const [profile, setProfile] = useState<DriverProfileSummary | null>(null);
  // PAN, saved on its own - see savePan.
  const [pan, setPan] = useState('');
  const [savingPan, setSavingPan] = useState(false);
  const [panError, setPanError] = useState<string | null>(null);
  const [panSaved, setPanSaved] = useState(false);

  /** Clears the input so re-picking the same file still fires a change. */
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
  }

  useEffect(load, []);

  useEffect(() => {
    usersApi
      .getMyProfile()
      .then((p) => {
        setProfile(p);
        setPan(p.panNumber ?? '');
      })
      .catch(() => {
        // The verification rows still render. Worst case the banner is a
        // little less specific, which is better than the screen failing.
      });
  }, []);

  /**
   * Both documents go up together, in one request.
   * <p>
   * They are picked separately and held here until both are chosen, because
   * the backend takes them as one submission - the ID and the registration
   * certificate are evidence for a single review decision. Sending one on
   * its own would put her in the operator's queue as a row nobody can
   * action, which looks to her like she has applied and to them like
   * nothing to do.
   */
  async function handleSubmit() {
    if (!idFile || !rcFile || !selfie) return;
    setUploading(true);
    setError(null);
    try {
      const updated = await verificationApi.uploadDocuments(idFile, rcFile, selfie);
      setSummary(updated);
      setIdFile(null);
      setRcFile(null);
      setSelfie(null);
    } catch (err) {
      // A spent challenge means a new selfie, not a retry that fails the same way.
      if (err instanceof ApiError && err.body?.error === 'SELFIE_CHALLENGE_EXPIRED') setSelfie(null);
      setError(err instanceof ApiError ? err.message : t('verification.uploadError'));
    } finally {
      setUploading(false);
    }
  }

  /**
   * Saves the PAN on its own, not with the documents.
   * <p>
   * It goes to the profile rather than to this review: an operator deciding
   * whether she is who she says she is has no use for it, and it is needed
   * later, when a payout is processed and tax has to be deducted against a
   * number. Saving it separately also means a failed upload does not lose
   * it, and re-submitting documents does not re-ask for it.
   */
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
  // Ten characters in the PAN shape, or empty to remove one already saved.
  const panUsable = pan.trim() === '' ? panOnFile : /^[A-Z]{5}[0-9]{4}[A-Z]$/.test(pan.trim().toUpperCase());

  const bothVerified = summary?.genderVerificationStatus === 'VERIFIED' && summary?.policeVerificationStatus === 'VERIFIED';
  const hasPhoto = Boolean(profile?.hasProfilePhoto);
  const readyToWork = bothVerified && hasPhoto;
  const canUpload = summary && summary.genderVerificationStatus !== 'VERIFIED' && summary.genderVerificationStatus !== 'UNDER_REVIEW';

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('verification.title')} onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!summary && !error && <SkeletonCard lines={3} label={t('verification.loading')} />}

      {summary && (
        <>
          {/* This card used to say "You're fully verified - you can go
              online and accept trips" the moment both checks passed. That
              became untrue when a profile photo became a requirement for
              going online: a partner read it, tapped Go Online, and was
              refused for a reason this screen had never mentioned.
              Verification is only one of the two things standing between
              her and her first trip, so the card has to know about both. */}
          <Card tone={readyToWork ? 'success' : 'warning'} className="flex items-start gap-3">
            <IconCircle
              color={readyToWork ? 'green' : 'orange'}
              tone="soft"
              icon={readyToWork ? <ShieldCheck /> : <Clock />}
            />
            <div className="flex-1">
              <p className="font-heading font-semibold text-text-primary">
                {readyToWork
                  ? t('verification.readyTitle')
                  : bothVerified
                    ? t('verification.oneMoreTitle')
                    : t('verification.inProgressTitle')}
              </p>
              <p className="mt-1 text-xs text-text-secondary">
                {readyToWork
                  ? t('verification.readyBody')
                  : bothVerified
                    ? t('verification.oneMoreBody')
                    : t('verification.inProgressBody')}
              </p>
              {bothVerified && !hasPhoto && (
                <Button size="md" className="mt-3" onClick={() => navigate('/profile')}>
                  {t('verification.addPhoto')}
                </Button>
              )}
            </div>
          </Card>

          {/* Two separate checks, so two separate rows with a rule between
              them - they used to run together in one undivided block, which
              read as a single item with two badges. */}
          <Card className="divide-y divide-border p-0">
            <div className="flex items-center justify-between p-4">
              <div className="flex items-center gap-2">
                <IconCircle size="sm" tone="soft" icon={<CheckCircle2 />} />
                <span className="text-sm font-medium text-text-primary">{t('verification.gender')}</span>
              </div>
              <StatusBadge tone={statusTone(summary.genderVerificationStatus)}>{statusLabel(summary.genderVerificationStatus)}</StatusBadge>
            </div>

            <div className="flex items-center justify-between p-4">
              <div className="flex items-center gap-2">
                <IconCircle size="sm" tone="soft" icon={<ShieldCheck />} />
                <span className="text-sm font-medium text-text-primary">{t('verification.police')}</span>
              </div>
              {/* She submits nothing for this - SheOut runs it once her ID is
                  approved - so "Not submitted" was the wrong thing to tell her. */}
              <StatusBadge tone={statusTone(summary.policeVerificationStatus)}>
                {summary.policeVerificationStatus === 'PENDING'
                  ? summary.genderVerificationStatus === 'VERIFIED' ? t('verification.policeInProgress') : t('verification.policeAfterId')
                  : statusLabel(summary.policeVerificationStatus)}
              </StatusBadge>
            </div>

            {/* A real answer rather than the placeholder message this used to
                open: police verification has no step for her to submit -
                SheOut's team runs it - and she should know what each check
                is looking at. */}
            <div className="p-4">
              <button
                type="button"
                onClick={() => setExplaining((v) => !v)}
                aria-expanded={explaining}
                className="flex w-full items-center gap-2 text-xs font-medium text-primary"
                data-testid="how-verified"
              >
                <FileWarning className="h-3.5 w-3.5" /> {t('verification.howVerified')}
              </button>
              {explaining && (
                <div className="mt-3 space-y-2 text-xs text-text-secondary">
                  <p>{t('verification.howGender')}</p>
                  <p>{t('verification.howPolice')}</p>
                </div>
              )}
            </div>
          </Card>

          <Card className="space-y-4">
            <div className="flex items-center gap-3">
              <IconCircle tone="soft" icon={<FileText />} />
              <p className="font-heading font-semibold text-text-primary">
                {summary.documentSubmitted ? t('verification.docsSubmitted') : t('verification.uploadDocs')}
              </p>
            </div>
            {/* This used to explain the backend's single document slot to
                the driver, in those words. A partner does not have a
                backend; they have an ID and a phone. Same fact, said as a
                person would say it - the constraint is still described in
                this file's header comment, where it belongs. */}

            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <IconCircle size="sm" tone="soft" icon={<IdCard />} />
                <p className="text-sm font-semibold text-text-primary">{t('verification.yourId')}</p>
              </div>
              <p className="text-xs text-text-secondary">
                {t('verification.yourIdBody')}
              </p>
              <p className="text-xs text-text-secondary">{t('verification.fileHint')}</p>
              {/* The number is optional to show. A photo gets the chance to
                  have it covered; a PDF goes as it is. */}
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
                disabled={!canUpload || uploading}
                onClick={() => idInputRef.current?.click()}
              >
                {idFile ? idFile.name : t('verification.chooseId')}
              </Button>
            </div>

            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <IconCircle size="sm" tone="soft" color="orange" icon={<Car />} />
                <p className="text-sm font-semibold text-text-primary">{t('verification.yourRc')}</p>
              </div>
              <p className="text-xs text-text-secondary">
                {t('verification.yourRcBody')}
              </p>
              <p className="text-xs text-text-secondary">{t('verification.fileHint')}</p>
              <input
                ref={rcInputRef}
                type="file"
                accept="image/*,.pdf"
                className="hidden"
                onChange={(e) => pickFile(e, setRcFile)}
              />
              <Button
                fullWidth
                variant="secondary"
                icon={rcFile ? <Check className="h-4 w-4" /> : <Upload className="h-4 w-4" />}
                disabled={!canUpload || uploading}
                onClick={() => rcInputRef.current?.click()}
              >
                {rcFile ? rcFile.name : t('verification.chooseRc')}
              </Button>
            </div>

            {/* Disabled until both are chosen. The button says which one is
                still missing rather than sitting greyed out with no
                explanation - a dead control is its own dead end. */}
            {/* Required, and camera only - see LiveSelfieCapture. */}
            {canUpload && (
              <div className="space-y-2">
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
              </div>
            )}

            <Button fullWidth disabled={!canUpload || uploading || !idFile || !rcFile || !selfie} onClick={handleSubmit} data-testid="verification-submit">
              {uploading
                ? t('common.uploading')
                : !idFile && !rcFile
                  ? t('verification.chooseBoth')
                  : !idFile
                    ? t('verification.chooseIdToContinue')
                    : !rcFile
                      ? t('verification.chooseRcToContinue')
                      : !selfie
                        ? t('verification.needSelfie')
                      : summary.documentSubmitted
                        ? t('verification.resubmit')
                        : t('verification.submit')}
            </Button>
          </Card>

          {/* PAN, and deliberately nothing to do with the review above.
              It is asked for here because this is the screen where she is
              already dealing with paperwork, and asking at payout time
              means somebody waiting on documents for money she has already
              earned. It is optional, it is not an identity check, and no
              part of the app withholds anything for its absence. */}
          <Card className="space-y-3">
            <div className="flex items-start gap-3">
              <IconCircle tone="soft" color="green" icon={<IdCard />} />
              <div>
                <p className="font-heading font-semibold text-text-primary">{t('verification.panTitle')}</p>
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
            {panSaved && !panChanged && <p className="text-xs text-success">{t('common.saved')}</p>}
            <Button
              fullWidth
              variant="secondary"
              disabled={savingPan || !panChanged || !panUsable}
              onClick={savePan}
            >
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
