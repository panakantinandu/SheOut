import { CheckCircle2, Clock, FileWarning, ShieldCheck, Upload } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, IconCircle, StatusBadge, TopHeader } from '@sheout/design-system';
import type { StatusTone } from '@sheout/design-system';
import { ApiError, verificationApi } from '../api/client';
import type { VerificationStatus, VerificationSummary } from '../api/types';
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
  return status ?? 'NOT APPLICABLE';
}

/**
 * REAL: status fetched from GET /api/v1/driver-verification/me, document
 * upload posts to POST /api/v1/driver-verification/documents (multipart).
 * <p>
 * FLAGGED GAP: the backend has exactly one generic document slot (always
 * stored server-side as "aadhaar" - see VerificationService), and no
 * submission endpoint at all for police verification (admin-only, no
 * driver action possible). So this screen has one real upload button, not
 * separate ones per document type - a "vehicle documents" upload button
 * would have nothing to call, so it isn't shown as a real action.
 */
export function Verification() {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [summary, setSummary] = useState<VerificationSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);

  function load() {
    verificationApi
      .getMyStatus()
      .then(setSummary)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load verification status'));
  }

  useEffect(load, []);

  async function handleFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const updated = await verificationApi.uploadDocument(file);
      setSummary(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not upload document');
    } finally {
      setUploading(false);
    }
  }

  const bothVerified = summary?.genderVerificationStatus === 'VERIFIED' && summary?.policeVerificationStatus === 'VERIFIED';
  const canUpload = summary && summary.genderVerificationStatus !== 'VERIFIED' && summary.genderVerificationStatus !== 'UNDER_REVIEW';

  return (
    <div className="space-y-6">
      <TopHeader variant="back" title="Verification" onBack={() => navigate('/home')} />

      {error && <p className="text-sm text-danger">{error}</p>}
      {!summary && !error && <p className="text-center text-sm text-text-secondary">Loading...</p>}

      {summary && (
        <>
          <Card className={bothVerified ? 'flex items-center gap-3 bg-accent-green/10' : 'flex items-center gap-3 bg-accent-orange/10'}>
            <IconCircle color={bothVerified ? 'green' : 'orange'} tone="soft" icon={bothVerified ? <ShieldCheck /> : <Clock />} />
            <div>
              <p className="font-heading font-semibold text-text-primary">
                {bothVerified ? "You're fully verified" : 'Verification in progress'}
              </p>
              <p className="text-xs text-text-secondary">
                {bothVerified ? 'You can go online and accept trips.' : "Complete the steps below before you can go online."}
              </p>
            </div>
          </Card>

          <Card className="space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <IconCircle size="sm" tone="soft" icon={<CheckCircle2 />} />
                <span className="text-sm font-medium text-text-primary">Gender Verification</span>
              </div>
              <StatusBadge tone={statusTone(summary.genderVerificationStatus)}>{statusLabel(summary.genderVerificationStatus)}</StatusBadge>
            </div>

            <div className="flex items-center justify-between">
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
              onClick={() => mockAction('Submit police verification', 'admin-only on the backend - no driver submission endpoint exists')}
              className="flex items-center gap-2 text-xs text-text-secondary underline"
            >
              <FileWarning className="h-3.5 w-3.5" /> How is this verified?
            </button>
          </Card>

          <Card className="space-y-3">
            <p className="text-sm font-medium text-text-primary">
              {summary.documentSubmitted ? 'Document submitted' : 'Upload your ID document'}
            </p>
            <p className="text-xs text-text-secondary">
              Used for gender verification. One document only (Aadhaar) - the backend has a single document slot, not separate uploads per document type.
            </p>
            <input ref={fileInputRef} type="file" accept="image/*,.pdf" className="hidden" onChange={handleFileSelected} />
            <Button
              fullWidth
              variant="secondary"
              icon={<Upload className="h-4 w-4" />}
              disabled={!canUpload || uploading}
              onClick={() => fileInputRef.current?.click()}
            >
              {uploading ? 'Uploading...' : summary.documentSubmitted ? 'Re-upload document' : 'Upload document'}
            </Button>
          </Card>
        </>
      )}
    </div>
  );
}
