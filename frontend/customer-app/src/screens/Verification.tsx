import { BadgeCheck, ShieldCheck, Upload } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, SkeletonCard, StatusBadge, TopHeader, verificationStatusLabel } from '@sheout/design-system';
import { ApiError, verificationApi } from '../api/client';
import type { VerificationSummary } from '../api/types';
import { useTranslation } from '@sheout/design-system';

const MAX_BYTES = 10 * 1024 * 1024; // matches the backend's multipart limit

/**
 * Lets a customer submit an ID document for gender verification.
 * <p>
 * This closes a real gap: booking has always required the customer to be
 * gender-verified, but nothing in this app let them get verified. Only the
 * driver app had an upload screen, so a customer hit "not verified" at
 * booking time with no way to do anything about it.
 * <p>
 * It reuses POST /api/v1/driver-verification/documents rather than adding a
 * customer-specific endpoint. Despite the path, that endpoint is not
 * driver-specific: it authenticates the caller and works from their account
 * id and verification record alone, with no reference to a vehicle or
 * driver profile. A second endpoint doing the same work would be two things
 * to keep in step for no gain.
 * <p>
 * Customers have no police check - that column is null for every CUSTOMER
 * record - so this screen only ever shows the one check. Approval stays a
 * human decision in the ops console; nothing here verifies anything
 * automatically.
 */
export function Verification() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [status, setStatus] = useState<VerificationSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    verificationApi
      .getMyStatus()
      .then(setStatus)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('verification.loadError')));
  }, []);

  async function handleFile(file: File) {
    setError(null);
    setNotice(null);
    if (file.size > MAX_BYTES) {
      setError(t('verification.tooLarge'));
      return;
    }
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
              <p className="mt-1 text-sm text-text-secondary">
                {isVerified
                  ? t('verification.verifiedBody')
                  : isUnderReview
                    ? t('verification.reviewBody')
                    : isRejected
                      ? t('verification.rejectedBody')
                      : t('verification.pendingBody')}
              </p>
            </div>
          </Card>

          {!isVerified && (
            <Card className="space-y-3">
              <p className="font-heading font-semibold text-text-primary">
                {isUnderReview ? t('verification.replace') : t('verification.upload')}
              </p>
              <p className="text-sm text-text-secondary">
                {t('verification.acceptedIds')}
              </p>
              {/* What the server will actually take, said before she picks
                  rather than after it is refused - see DocumentRules. */}
              <p className="text-xs text-text-secondary">{t('verification.fileHint')}</p>

              <input
                ref={fileRef}
                type="file"
                accept="image/*,application/pdf"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) handleFile(file);
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

              <p className="text-xs text-text-secondary">
                {t('verification.privacyNote')}
              </p>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
