import { Check, Upload } from 'lucide-react';
import { useRef, useState, type ReactNode } from 'react';
import { Button, StatusBadge, TextField, useTranslation } from '@sheout/design-system';
import type { StatusTone } from '@sheout/design-system';
import { ApiError, verificationApi } from '../api/client';
import type { DocumentState, InsuranceUseType, PartnerDocumentType } from '../api/types';
import { daysUntil, formatDay } from '../lib/documentDates';

/** Documents that carry a printed valid-until date. The police certificate is redone on a period instead. */
const EXPIRES: PartnerDocumentType[] = ['DRIVING_LICENCE', 'VEHICLE_RC', 'VEHICLE_INSURANCE', 'PUC', 'FITNESS_CERTIFICATE'];

export function documentTone(state: DocumentState | undefined): StatusTone {
  if (!state || !state.status) return 'warning';
  if (state.renewalUnderReview) return 'success';
  switch (state.status) {
    case 'VERIFIED':
      return state.validUntil && (daysUntil(state.validUntil) ?? 99) <= 30 ? 'warning' : 'success';
    case 'REJECTED':
    case 'EXPIRED':
      return 'danger';
    default:
      return 'primary';
  }
}

/**
 * One document on her checklist: where it stands, why if it was turned
 * down, when it runs out, and the form to send it (again).
 * <p>
 * She types what is printed on it - the number, the dates, and for a
 * policy whether it is for commercial use. An operator checks her answer
 * against the photo and corrects it, rather than transcribing it, which is
 * what makes a review minutes rather than a day.
 */
export function DocumentStep({
  type,
  state,
  icon,
  disabled,
  extra,
  onSent,
}: {
  type: PartnerDocumentType;
  state: DocumentState | undefined;
  icon: ReactNode;
  /** Consent not given yet: nothing can be sent. */
  disabled: boolean;
  /** Anything specific to this document, shown above the form (the police portal steps). */
  extra?: ReactNode;
  onSent: () => void;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [number, setNumber] = useState(state?.documentNumber ?? '');
  const [issuedOn, setIssuedOn] = useState('');
  const [validUntil, setValidUntil] = useState('');
  const [use, setUse] = useState<InsuranceUseType | ''>('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const expires = EXPIRES.includes(type);
  const name = t(`docs.type.${type}`);
  const days = state?.validUntil ? daysUntil(state.validUntil) : null;

  const status = !state?.status
    ? t('docs.status.missing')
    : state.renewalUnderReview
      ? t('docs.status.renewal')
      : state.status === 'VERIFIED' && days !== null && days <= 30
        ? t('docs.status.expiringSoon')
        : t(`docs.status.${state.status}`);

  const ready = Boolean(file) && number.trim() !== '' && (!expires || validUntil !== '') && (type !== 'VEHICLE_INSURANCE' || use !== '');
  const missing = !file
    ? t('docs.needFile')
    : number.trim() === ''
      ? t('docs.needNumber')
      : expires && !validUntil
        ? t('docs.needValidUntil')
        : type === 'VEHICLE_INSURANCE' && !use
          ? t('docs.needUse')
          : null;

  async function send() {
    if (!file || !ready) return;
    setSending(true);
    setError(null);
    try {
      await verificationApi.uploadPartnerDocument(type, file, {
        documentNumber: number.trim(),
        issuedOn: issuedOn || undefined,
        validUntil: validUntil || undefined,
        insuranceUseType: use || undefined,
      });
      setOpen(false);
      setFile(null);
      onSent();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('docs.sendError'));
    } finally {
      setSending(false);
    }
  }

  const actionLabel = !state?.status
    ? t('docs.upload')
    : state.status === 'VERIFIED' && !state.renewalUnderReview
      ? t('docs.uploadRenewal')
      : t('docs.reupload');
  const canSend = !state?.renewalUnderReview && state?.status !== 'UNDER_REVIEW' && state?.status !== 'PENDING';

  return (
    <div className="space-y-2 p-4" data-testid={`doc-step-${type}`}>
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          {icon}
          <span className="text-sm font-medium text-text-primary">{name}</span>
        </div>
        <StatusBadge tone={documentTone(state)}>{status}</StatusBadge>
      </div>

      {state?.status === 'REJECTED' && state.rejectionReason && (
        <p className="text-xs text-danger">{t('docs.rejectedBecause', { reason: state.rejectionReason })}</p>
      )}
      {state?.validUntil && state.status === 'VERIFIED' && (
        <p className={`text-xs ${days !== null && days <= 30 ? 'text-warning' : 'text-text-secondary'}`}>
          {days !== null && days <= 30
            ? t('docs.expiresSoon', { date: formatDay(state.validUntil), count: Math.max(days, 0) })
            : t('docs.validUntil', { date: formatDay(state.validUntil) })}
        </p>
      )}
      {(state?.status === 'EXPIRED' || (days !== null && days < 0)) && state?.validUntil && (
        <p className="text-xs text-danger">{t('docs.expiredOn', { date: formatDay(state.validUntil) })}</p>
      )}
      {state?.insuranceUseType && state.insuranceUseType !== 'COMMERCIAL' && state.status !== 'VERIFIED' && (
        <p className="text-xs text-danger">{t('docs.privateWarning')}</p>
      )}

      {extra}

      {canSend && !open && (
        <Button
          size="md"
          variant="secondary"
          icon={<Upload className="h-4 w-4" />}
          disabled={disabled}
          onClick={() => setOpen(true)}
          data-testid={`doc-open-${type}`}
        >
          {actionLabel}
        </Button>
      )}
      {disabled && !state?.status && <p className="text-xs text-text-secondary">{t('docs.needConsentFirst')}</p>}

      {open && (
        <div className="space-y-3 rounded-card bg-background p-3">
          <input
            ref={input}
            type="file"
            accept="image/*,.pdf"
            className="hidden"
            onChange={(e) => {
              setFile(e.target.files?.[0] ?? null);
              e.target.value = '';
            }}
          />
          <Button
            fullWidth
            variant="secondary"
            icon={file ? <Check className="h-4 w-4" /> : <Upload className="h-4 w-4" />}
            onClick={() => input.current?.click()}
          >
            {file ? file.name : t('docs.chooseFile', { document: name })}
          </Button>
          <p className="text-xs text-text-secondary">{t('verification.fileHint')}</p>
          <TextField label={t('docs.number')} value={number} maxLength={64} onChange={(e) => setNumber(e.target.value)} />
          <TextField label={t('docs.issuedOn')} type="date" value={issuedOn} onChange={(e) => setIssuedOn(e.target.value)} />
          {expires && (
            <TextField label={t('docs.validUntilField')} type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
          )}
          {type === 'VEHICLE_INSURANCE' && (
            <fieldset className="space-y-1">
              <legend className="text-xs font-medium text-text-secondary">{t('docs.useQuestion')}</legend>
              {(['COMMERCIAL', 'PRIVATE', 'UNKNOWN'] as InsuranceUseType[]).map((value) => (
                <label key={value} className="flex items-center gap-2 text-sm text-text-primary">
                  <input type="radio" name={`use-${type}`} checked={use === value} onChange={() => setUse(value)} />
                  {t(`docs.use.${value}`)}
                </label>
              ))}
              {use && use !== 'COMMERCIAL' && <p className="text-xs text-danger">{t('docs.privateWarning')}</p>}
            </fieldset>
          )}
          {error && <p className="text-xs text-danger">{error}</p>}
          <div className="flex gap-2">
            <Button fullWidth disabled={!ready || sending} onClick={send} data-testid={`doc-send-${type}`}>
              {sending ? t('common.uploading') : missing ?? t('docs.send')}
            </Button>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              {t('docs.cancel')}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
