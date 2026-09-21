import { BadgeCheck, Clock, Eye, ShieldCheck, Upload, UserCheck } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  DocumentMasker,
  IconCircle,
  SkeletonCard,
  StatusBadge,
  TopHeader,
  verificationStatusLabel,
} from '@sheout/design-system';
import { ApiError, verificationApi } from '../api/client';
import type { VerificationSummary, VerificationTurnaround } from '../api/types';
import { useTranslation } from '@sheout/design-system';

const MAX_BYTES = 10 * 1024 * 1024; // matches the backend's multipart limit

/**
 * Where a rider gets verified as a woman, and is told why that is being
 * asked at all.
 * <p>
 * IT EXPLAINS BEFORE IT ASKS. The screen used to open with "Upload a
 * government ID", which is a compliance notice: it tells somebody what to
 * surrender without telling her what it buys her. The thing she is actually
 * being offered is that every other person on this platform went through
 * the same door, and that is what the first card says now.
 * <p>
 * IT ASKS FOR LESS THAN IT COULD. A rider is verified from a name, a face
 * and a gender marker. The Aadhaar number is not part of that decision, and
 * holding one is a liability to her - it is the number behind her bank, her
 * SIM and her benefits. So she is invited to cover it before sending, and
 * what we store is what she chose to show us (see DocumentMasker). Partners
 * are a different case and still send the whole document: they carry
 * riders, and that trust is checked further, including a police record.
 * <p>
 * IT SAYS HOW LONG. The wait is a person reading a document, so the screen
 * asks the server what reviews have actually been taking lately and says
 * that - as a measured "usually" once there are enough of them, and as an
 * honest "we aim to" before that.
 * <p>
 * It reuses POST /api/v1/driver-verification/documents. Despite the path
 * that endpoint is not driver-specific: it works from the caller's account
 * and verification record alone.
 */
export function Verification() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [status, setStatus] = useState<VerificationSummary | null>(null);
  const [turnaround, setTurnaround] = useState<VerificationTurnaround | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  /** Chosen, not yet sent: she is covering her number first. */
  const [masking, setMasking] = useState<File | null>(null);
  const fileRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    verificationApi
      .getMyStatus()
      .then((summary) => {
        setStatus(summary);
        // Only counted when there is genuinely nothing submitted: coming
        // back to look at "being reviewed" is not reaching the upload step.
        if (!summary.documentSubmitted) void verificationApi.recordProgress('UPLOAD_VIEWED');
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('verification.loadError')));
    verificationApi.turnaround().then(setTurnaround).catch(() => undefined);
  }, []);

  function chooseFile(file: File) {
    setError(null);
    setNotice(null);
    if (file.size > MAX_BYTES) {
      setError(t('verification.tooLarge'));
      return;
    }
    void verificationApi.recordProgress('DOCUMENT_CHOSEN');
    // A PDF cannot be drawn on here, so it goes as it is; a photo gets the
    // chance to have its number covered first.
    if (file.type.startsWith('image/')) {
      setMasking(file);
      return;
    }
    void send(file);
  }

  async function send(file: File) {
    setMasking(null);
    setError(null);
    setUploading(true);
    try {
      const updated = await verificationApi.submitDocument(file);
      setStatus(updated);
      setNotice(t('verification.submitted'));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('verification.uploadError'));
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  }

  const gender = status?.genderVerificationStatus;
  const isVerified = gender === 'VERIFIED';
  const isUnderReview = gender === 'UNDER_REVIEW';
  const isRejected = gender === 'REJECTED';

  /** "about 4 hours" / "about 30 minutes", from what reviews really take. */
  const waitText = turnaround
    ? turnaround.typicalMinutes >= 60
      ? t('verification.waitHours', { count: Math.round(turnaround.typicalMinutes / 60) })
      : t('verification.waitMinutes', { count: turnaround.typicalMinutes })
    : null;
  const waitLine = waitText
    ? turnaround?.measured
      ? t('verification.waitUsually', { time: waitText })
      : t('verification.waitTarget', { time: waitText })
    : null;

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title={t('verification.title')} onBack={() => navigate(-1)} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {notice && (
        <p className="rounded-input bg-primary-light px-4 py-3 text-sm font-medium text-primary">{notice}</p>
      )}

      {!status ? (
        <SkeletonCard lines={3} label={t('verification.loading')} />
      ) : (
        <>
          <Card className="flex items-start gap-3">
            <IconCircle
              size="lg"
              tone="soft"
              color={isVerified ? 'green' : isRejected ? 'red' : 'orange'}
              icon={isVerified ? <BadgeCheck /> : <ShieldCheck />}
            />
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <p className="font-heading font-semibold text-text-primary">{t('verification.idVerification')}</p>
                <StatusBadge tone={isVerified ? 'success' : isRejected ? 'danger' : 'warning'}>
                  {verificationStatusLabel(gender ?? 'PENDING')}
                </StatusBadge>
              </div>
              <p className="mt-1 text-sm leading-relaxed text-text-secondary">
                {isVerified
                  ? t('verification.verifiedBody')
                  : isUnderReview
                    ? t('verification.reviewBody')
                    : isRejected
                      ? t('verification.rejectedBody')
                      : t('verification.whyBody')}
              </p>
              {/* The rejection is useless without the reason an operator
                  wrote; it is the whole difference between "try again" and
                  "try again with a clearer photo of the front". */}
              {isRejected && status.rejectionReason && (
                <p className="mt-2 rounded-input bg-background px-3 py-2 text-sm text-text-primary" data-testid="rejection-reason">
                  {status.rejectionReason}
                </p>
              )}
            </div>
          </Card>

          {/* While she waits: how long, and that she is not stuck here. */}
          {isUnderReview && (
            <Card className="space-y-3" data-testid="verification-waiting">
              <div className="flex items-start gap-3">
                <IconCircle size="sm" tone="soft" icon={<Clock />} />
                <div>
                  <p className="text-sm font-semibold text-text-primary">{waitLine ?? t('verification.waitUnknown')}</p>
                  <p className="mt-1 text-sm text-text-secondary">{t('verification.waitBody')}</p>
                </div>
              </div>
              <Button variant="secondary" fullWidth onClick={() => navigate('/home')}>
                {t('verification.exploreMeanwhile')}
              </Button>
            </Card>
          )}

          {!isVerified && !masking && (
            <Card className="space-y-3">
              <p className="font-heading font-semibold text-text-primary">
                {isUnderReview ? t('verification.replace') : t('verification.upload')}
              </p>
              <p className="text-sm text-text-secondary">{t('verification.acceptedIds')}</p>

              {/* What is actually looked at, said before she sends anything.
                  It is also the argument for covering the number. */}
              <div className="space-y-2 rounded-card bg-background p-3">
                <p className="flex items-center gap-2 text-sm font-semibold text-text-primary">
                  <UserCheck className="h-4 w-4 text-primary" aria-hidden="true" />
                  {t('verification.whatWeNeed')}
                </p>
                <p className="text-xs leading-relaxed text-text-secondary">{t('verification.maskInvite')}</p>
              </div>

              <p className="text-xs text-text-secondary">{t('verification.fileHint')}</p>

              <input
                ref={fileRef}
                type="file"
                accept="image/*,application/pdf"
                className="hidden"
                data-testid="verification-file"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) chooseFile(file);
                }}
              />
              <Button
                fullWidth
                disabled={uploading}
                icon={<Upload className="h-4 w-4" />}
                onClick={() => fileRef.current?.click()}
              >
                {uploading ? t('common.uploading') : status.documentSubmitted ? t('verification.uploadDifferent') : t('verification.choose')}
              </Button>

              <p className="flex items-start gap-2 text-xs leading-relaxed text-text-secondary">
                <Eye className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                {t('verification.privacyNote')}
              </p>
            </Card>
          )}

          {masking && (
            <Card>
              <DocumentMasker
                file={masking}
                busy={uploading}
                onCancel={() => setMasking(null)}
                onDone={(masked) => void send(masked)}
              />
            </Card>
          )}
        </>
      )}
    </div>
  );
}
