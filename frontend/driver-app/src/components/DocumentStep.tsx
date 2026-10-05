import { useState } from 'react';
import { Button, TextField, useTranslation } from '@sheout/design-system';
import { ApiError, verificationApi } from '../api/client';
import type { DocumentState, InsuranceUseType, PartnerDocumentType } from '../api/types';
import { daysUntil, todayInIndia } from '../lib/documentDates';
import { prepareDocumentPhoto } from '../lib/documentPhoto';
import { PhotoPicker } from './verification/PhotoPicker';

/** Documents that carry a printed valid-until date. The police certificate is redone on a period instead. */
const EXPIRES: PartnerDocumentType[] = ['DRIVING_LICENCE', 'VEHICLE_RC', 'VEHICLE_INSURANCE', 'PUC', 'FITNESS_CERTIFICATE'];

/**
 * Sending one document: a photo of it, then what is printed on it - the
 * number and the date it runs out, and for a policy whether it is
 * commercial. Under every box, where on the paper to find it ("Printed as
 * 'DL No.'"), because "Number on the document" left her guessing which of
 * five numbers on a licence we meant.
 * <p>
 * An operator checks her answer against the photo and corrects it, rather
 * than transcribing it, which is what makes a review minutes rather than a
 * day. A date that has already passed is caught here, before the upload,
 * not after it as a server refusal.
 */
export function DocumentForm({
  type,
  state,
  onSent,
  onCancel,
}: {
  type: PartnerDocumentType;
  state: DocumentState | undefined;
  onSent: () => void;
  onCancel?: () => void;
}) {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [number, setNumber] = useState(state?.documentNumber ?? '');
  const [issuedOn, setIssuedOn] = useState('');
  const [validUntil, setValidUntil] = useState('');
  const [use, setUse] = useState<InsuranceUseType | ''>('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const expires = EXPIRES.includes(type);
  const police = type === 'POLICE_CERTIFICATE';
  const pastDate = expires && validUntil !== '' && (daysUntil(validUntil) ?? 0) < 0;

  const missing = !file
    ? t('verify.doc.needFile')
    : number.trim() === ''
      ? t('verify.doc.needNumber')
      : expires && !validUntil
        ? t('verify.doc.needValidUntil')
        : type === 'VEHICLE_INSURANCE' && !use
          ? t('verify.doc.needUse')
          : null;

  async function send() {
    if (!file || missing || pastDate) return;
    setSending(true);
    setError(null);
    try {
      await verificationApi.uploadPartnerDocument(type, await prepareDocumentPhoto(file), {
        documentNumber: number.trim(),
        issuedOn: police && issuedOn ? issuedOn : undefined,
        validUntil: validUntil || undefined,
        insuranceUseType: use || undefined,
      });
      setFile(null);
      onSent();
    } catch (err) {
      setError(
        err instanceof ApiError && err.body?.error === 'DOCUMENT_TOO_SMALL'
          ? t('verify.doc.photoTooSmall')
          : err instanceof ApiError
            ? err.message
            : t('verify.doc.sendError'),
      );
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="space-y-4" data-testid={`doc-form-${type}`}>
      <PhotoPicker file={file} onPick={setFile} disabled={sending} testId={`doc-photo-${type}`} />

      <div className="space-y-1">
        <TextField
          label={t(`verify.doc.number.${type}`)}
          value={number}
          maxLength={64}
          autoCapitalize="characters"
          autoComplete="off"
          onChange={(e) => setNumber(e.target.value)}
          data-testid={`doc-number-${type}`}
        />
        <p className="px-1 text-caption text-text-secondary">{t(`verify.doc.numberHint.${type}`)}</p>
      </div>

      {expires && (
        <div className="space-y-1">
          <TextField
            label={t('verify.doc.validUntil')}
            type="date"
            min={todayInIndia()}
            value={validUntil}
            onChange={(e) => setValidUntil(e.target.value)}
            error={pastDate ? t('verify.doc.pastDate') : undefined}
            data-testid={`doc-valid-${type}`}
          />
          {!pastDate && <p className="px-1 text-caption text-text-secondary">{t(`verify.doc.validHint.${type}`)}</p>}
        </div>
      )}

      {police && (
        <TextField
          label={t('verify.doc.issuedOn')}
          type="date"
          max={todayInIndia()}
          value={issuedOn}
          onChange={(e) => setIssuedOn(e.target.value)}
        />
      )}

      {type === 'VEHICLE_INSURANCE' && (
        <fieldset className="space-y-2">
          <legend className="mb-1 text-sm font-medium text-text-primary">{t('verify.doc.useQuestion')}</legend>
          <div className="grid gap-2">
            {(['COMMERCIAL', 'PRIVATE', 'UNKNOWN'] as InsuranceUseType[]).map((value) => (
              <label
                key={value}
                className={`flex cursor-pointer items-center gap-3 rounded-input border px-3 py-2.5 text-sm ${
                  use === value ? 'border-primary bg-primary-light text-text-primary' : 'border-border text-text-primary'
                }`}
              >
                <input type="radio" name={`use-${type}`} checked={use === value} onChange={() => setUse(value)} className="accent-primary" />
                {t(`verify.doc.use.${value}`)}
              </label>
            ))}
          </div>
          <p className="px-1 text-caption text-text-secondary">{t('verify.doc.useHelp')}</p>
          {use && use !== 'COMMERCIAL' && <p className="px-1 text-caption text-danger">{t('verify.doc.privateWarning')}</p>}
        </fieldset>
      )}

      {error && <p className="text-sm text-danger" role="alert">{error}</p>}
      <div className="flex gap-2">
        <Button fullWidth disabled={Boolean(missing) || pastDate || sending} onClick={send} data-testid={`doc-send-${type}`}>
          {sending ? t('common.uploading') : missing ?? t('verify.doc.send')}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={sending}>
            {t('verify.doc.cancel')}
          </Button>
        )}
      </div>
    </div>
  );
}
