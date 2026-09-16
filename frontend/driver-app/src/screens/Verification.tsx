import { CheckCircle2, Clock, FileWarning, ShieldCheck, Check, Upload } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, StatusBadge, TextField, TopHeader, verificationStatusLabel } from '@sheout/design-system';
import type { StatusTone } from '@sheout/design-system';
import { ApiError, usersApi, verificationApi } from '../api/client';
import type { DriverProfileSummary, VerificationStatus, VerificationSummary } from '../api/types';
import { mockAction } from '../lib/mockAction';

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
  const navigate = useNavigate();
  const idInputRef = useRef<HTMLInputElement>(null);
  const rcInputRef = useRef<HTMLInputElement>(null);
  const [idFile, setIdFile] = useState<File | null>(null);
  const [rcFile, setRcFile] = useState<File | null>(null);
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
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load verification status'));
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
    if (!idFile || !rcFile) return;
    setUploading(true);
    setError(null);
    try {
      const updated = await verificationApi.uploadDocuments(idFile, rcFile);
      setSummary(updated);
      setIdFile(null);
      setRcFile(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not upload your documents');
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
      setPanError(err instanceof ApiError ? err.message : 'Could not save your PAN');
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
      <TopHeader variant="back" title="Verification" onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!summary && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}

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
                  ? "You're ready to work"
                  : bothVerified
                    ? 'One more thing'
                    : 'Verification in progress'}
              </p>
              <p className="mt-1 text-xs text-text-secondary">
                {readyToWork
                  ? 'Both checks are approved and your photo is on file. You can go online and accept trips.'
                  : bothVerified
                    ? 'Both checks are approved. Add a profile photo and you can go online - riders use it to check they have the right vehicle.'
                    : 'Complete the steps below before you can go online.'}
              </p>
              {bothVerified && !hasPhoto && (
                <Button size="md" className="mt-3" onClick={() => navigate('/profile')}>
                  Add your photo
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
                <span className="text-sm font-medium text-text-primary">Gender Verification</span>
              </div>
              <StatusBadge tone={statusTone(summary.genderVerificationStatus)}>{statusLabel(summary.genderVerificationStatus)}</StatusBadge>
            </div>

            <div className="flex items-center justify-between p-4">
              <div className="flex items-center gap-2">
                <IconCircle size="sm" tone="soft" icon={<ShieldCheck />} />
                <span className="text-sm font-medium text-text-primary">Police Verification</span>
              </div>
              <StatusBadge tone={statusTone(summary.policeVerificationStatus)}>{statusLabel(summary.policeVerificationStatus)}</StatusBadge>
            </div>

            {/* MOCK: police verification has no driver-facing submission step on the
                backend at all - it's admin-only, so there's nothing real this button can do. */}
            <button
              type="button"
              onClick={() => mockAction('Submit police verification', 'our team runs this check for you - there is nothing to submit')}
              className="flex w-full items-center gap-2 p-4 text-xs text-text-secondary underline"
            >
              <FileWarning className="h-3.5 w-3.5" /> How is this verified?
            </button>
          </Card>

          <Card className="space-y-4">
            <p className="text-sm font-medium text-text-primary">
              {summary.documentSubmitted ? 'Documents submitted' : 'Upload your documents'}
            </p>
            {/* This used to explain the backend's single document slot to
                the driver, in those words. A partner does not have a
                backend; they have an ID and a phone. Same fact, said as a
                person would say it - the constraint is still described in
                this file's header comment, where it belongs. */}

            <div className="space-y-2">
              <p className="text-sm font-medium text-text-primary">1. Your ID</p>
              <p className="text-xs text-text-secondary">
                One government ID, used to confirm your identity. Aadhaar, passport, driving licence or voter ID. Make
                sure your name and photo are readable.
              </p>
              <input
                ref={idInputRef}
                type="file"
                accept="image/*,.pdf"
                className="hidden"
                onChange={(e) => pickFile(e, setIdFile)}
              />
              <Button
                fullWidth
                variant="secondary"
                icon={idFile ? <Check className="h-4 w-4" /> : <Upload className="h-4 w-4" />}
                disabled={!canUpload || uploading}
                onClick={() => idInputRef.current?.click()}
              >
                {idFile ? idFile.name : 'Choose ID document'}
              </Button>
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium text-text-primary">2. Your vehicle's RC</p>
              <p className="text-xs text-text-secondary">
                A photo of the registration certificate for the vehicle you drive. We check it against the registration
                number on your profile, so the number plate must be readable.
              </p>
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
                {rcFile ? rcFile.name : 'Choose RC photo'}
              </Button>
            </div>

            {/* Disabled until both are chosen. The button says which one is
                still missing rather than sitting greyed out with no
                explanation - a dead control is its own dead end. */}
            <Button fullWidth disabled={!canUpload || uploading || !idFile || !rcFile} onClick={handleSubmit}>
              {uploading
                ? 'Uploading...'
                : !idFile && !rcFile
                  ? 'Choose both documents'
                  : !idFile
                    ? 'Choose your ID to continue'
                    : !rcFile
                      ? 'Choose your RC photo to continue'
                      : summary.documentSubmitted
                        ? 'Re-submit both documents'
                        : 'Submit for review'}
            </Button>
          </Card>

          {/* PAN, and deliberately nothing to do with the review above.
              It is asked for here because this is the screen where she is
              already dealing with paperwork, and asking at payout time
              means somebody waiting on documents for money she has already
              earned. It is optional, it is not an identity check, and no
              part of the app withholds anything for its absence. */}
          <Card className="space-y-3">
            <div>
              <p className="text-sm font-medium text-text-primary">Your PAN (optional)</p>
              <p className="mt-1 text-xs text-text-secondary">
                For tax on your payouts. Tax is deducted and reported against a PAN when we pay you, so adding it now
                keeps that from holding up money you have earned. It is not part of your verification, and you can add
                it later.
              </p>
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
            {panSaved && !panChanged && <p className="text-xs text-success">Saved.</p>}
            <Button
              fullWidth
              variant="secondary"
              disabled={savingPan || !panChanged || !panUsable}
              onClick={savePan}
            >
              {savingPan
                ? 'Saving...'
                : pan.trim() === ''
                  ? 'Remove PAN'
                  : panOnFile
                    ? 'Update PAN'
                    : 'Save PAN'}
            </Button>
          </Card>
        </>
      )}
    </div>
  );
}
