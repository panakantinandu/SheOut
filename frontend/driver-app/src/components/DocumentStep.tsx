import { createContext, useContext, useState } from 'react';
import { Button, TextField, photoWarningsParam, useTranslation, type PhotoWarning } from '@sheout/design-system';
import { ApiError, verificationApi } from '../api/client';
import type { DocumentState, InsuranceUseType, PartnerDocumentType, VehicleOwner } from '../api/types';
import { daysUntil, formatDay, todayInIndia } from '../lib/documentDates';
import { prepareDocumentPhoto } from '../lib/documentPhoto';
import {
  NUMBER_MAX_LENGTH,
  earliestPoliceIssue,
  policeDueOn,
  isPlate,
  latestValidUntil,
  normalizeNumber,
  numberLooksRight,
} from '../lib/documentRules';
import { PhotoPicker } from './verification/PhotoPicker';

/** Documents that carry a printed valid-until date. The police certificate is redone on a period instead. */
const EXPIRES: PartnerDocumentType[] = ['DRIVING_LICENCE', 'VEHICLE_RC', 'VEHICLE_INSURANCE', 'PUC', 'FITNESS_CERTIFICATE'];

const OWNERS: VehicleOwner[] = ['SELF', 'FATHER', 'MOTHER', 'HUSBAND', 'SIBLING', 'OTHER_FAMILY'];

/**
 * What her profile says, for the licence form: the name she can declare
 * the licence matches, and the date of birth shown back to her before she
 * sends. Provided by the Verification screen.
 */
export const ApplicantFacts = createContext<{ name?: string | null; dateOfBirth?: string | null }>({});

/**
 * Sending one document: a photo of it, then what is printed on it - the
 * number and the date it runs out, and for a policy whether it is
 * commercial. Under every box, where on the paper to find it ("Printed as
 * 'DL No.'").
 * <p>
 * Each number must have the shape that document really has (see
 * documentRules and the server's DocumentNumberRules): a licence is 15
 * characters, an RC is a number plate, a policy has digits. She is told the
 * moment she leaves the box, not after an upload - because a reviewer can
 * only check a number against the photo if it is one.
 * <p>
 * The RC's number is the plate on her profile, already filled in: one plate,
 * typed once. Whose vehicle it is comes next - her own, or a family
 * member's with the owner's name as printed - because many women ride a
 * parent's or husband's scooter, and that is welcome; the reviewer then
 * checks the RC against that name instead of hers.
 */
export function DocumentForm({
  type,
  state,
  profilePlate,
  onSent,
  onCancel,
}: {
  type: PartnerDocumentType;
  state: DocumentState | undefined;
  /** The plate on her profile: the RC's number. */
  profilePlate?: string | null;
  onSent: () => void;
  onCancel?: () => void;
}) {
  const { t } = useTranslation();
  const plateFromProfile = type === 'VEHICLE_RC' && profilePlate && isPlate(profilePlate) ? profilePlate : null;
  const [file, setFile] = useState<File | null>(null);
  const [number, setNumber] = useState(plateFromProfile ?? state?.documentNumber ?? '');
  const [numberTouched, setNumberTouched] = useState(false);
  const [issuedOn, setIssuedOn] = useState('');
  const [validUntil, setValidUntil] = useState('');
  const [use, setUse] = useState<InsuranceUseType | ''>('');
  /** Only an auto or a cab needs a commercial policy; a bike taxi runs on her own bike's policy. */
  const commercialAsked = type === 'VEHICLE_INSURANCE' && Boolean(state?.commercialUseRequired);
  const [owner, setOwner] = useState<VehicleOwner | ''>('');
  const [ownerName, setOwnerName] = useState('');
  /** What the photo check noticed and she chose to send anyway. */
  const [photoWarnings, setPhotoWarnings] = useState<PhotoWarning[]>([]);
  /** A licence: the name exactly as printed, typed by her - never filled in for her. */
  const [nameOnDocument, setNameOnDocument] = useState('');
  /** A licence: she said the name is the same as her profile. Her word, recorded as that. */
  const [nameDeclared, setNameDeclared] = useState(false);
  /** Showing her what she is about to send, to confirm. */
  const [confirming, setConfirming] = useState(false);
  const applicant = useContext(ApplicantFacts);
  const licence = type === 'DRIVING_LICENCE';
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const expires = EXPIRES.includes(type);
  const police = type === 'POLICE_CERTIFICATE';
  const rc = type === 'VEHICLE_RC';
  const today = todayInIndia();
  const latest = latestValidUntil(type, today);
  const oldestPolice = earliestPoliceIssue(today);

  const numberOk = numberLooksRight(type, number);
  const numberError = numberTouched && number.trim() !== '' && !numberOk ? t(`verify.doc.numberFormat.${type}`) : undefined;
  const pastDate = expires && validUntil !== '' && (daysUntil(validUntil) ?? 0) < 0;
  const tooFar = expires && validUntil !== '' && latest !== null && validUntil > latest;
  const dateError = pastDate
    ? t(type === 'DRIVING_LICENCE' ? 'verify.doc.pastDateLicence' : 'verify.doc.pastDate')
    : tooFar ? t('verify.doc.tooFar') : undefined;
  const issueTooOld = police && issuedOn !== '' && issuedOn < oldestPolice;
  const issueInFuture = police && issuedOn !== '' && issuedOn > today;
  const issueError = issueTooOld ? t('verify.doc.policeTooOld') : issueInFuture ? t('verify.doc.issueFuture') : undefined;
  // Accepted, but due again soon: say so before she sends it, not two days later.
  const dueOn = police && issuedOn !== '' && !issueError ? policeDueOn(issuedOn) : null;
  const dueInDays = dueOn ? daysUntil(dueOn) : null;
  const dueSoon = dueInDays !== null && dueInDays <= 60;
  const ownerNameOk = owner === 'SELF' || /^[\p{L} .'-]{2,100}$/u.test(ownerName.trim());

  const missing = !file
    ? t('verify.doc.needFile')
    : number.trim() === ''
      ? t('verify.doc.needNumber')
      : !numberOk
        ? t('verify.doc.fixNumber')
        : expires && !validUntil
          ? t('verify.doc.needValidUntil')
          : police && !issuedOn
            ? t('verify.doc.needIssuedOn')
            : licence && !nameDeclared && nameOnDocument.trim().length < 2
              ? t('verify.doc.needNameOnLicence')
            : rc && !owner
              ? t('verify.doc.needOwner')
              : rc && !ownerNameOk
                ? t('verify.doc.needOwnerName')
                : commercialAsked && !use
                  ? t('verify.doc.needUse')
                  : null;
  const blocked = Boolean(missing) || Boolean(dateError) || Boolean(issueError);

  async function send() {
    if (!file || blocked) return;
    // A printed date is the easiest thing to mistype: she sees it back, in
    // words, before it goes - and her date of birth beside a licence's.
    if (expires && !confirming) {
      setConfirming(true);
      return;
    }
    setSending(true);
    setError(null);
    try {
      await verificationApi.uploadPartnerDocument(type, await prepareDocumentPhoto(file), {
        documentNumber: normalizeNumber(type, number),
        issuedOn: police && issuedOn ? issuedOn : undefined,
        validUntil: validUntil || undefined,
        insuranceUseType: use || undefined,
        vehicleOwner: rc && owner ? owner : undefined,
        ownerName: rc && owner && owner !== 'SELF' ? ownerName.trim() : undefined,
        nameOnDocument: licence && !nameDeclared ? nameOnDocument.trim() : undefined,
        nameDeclared: licence && nameDeclared ? true : undefined,
        photoWarnings: photoWarningsParam(photoWarnings),
      });
      setFile(null);
      setConfirming(false);
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
      <PhotoPicker
        file={file}
        onPick={(picked, warnings) => {
          setFile(picked);
          setPhotoWarnings(warnings);
          setConfirming(false);
        }}
        disabled={sending}
        testId={`doc-photo-${type}`}
      />

      {licence && (
        <div className="space-y-1" data-testid="licence-name">
          {nameDeclared ? (
            <div className="flex items-center justify-between gap-2 rounded-input border border-border bg-surface px-3 py-2.5">
              <span className="min-w-0 text-sm text-text-primary">
                {t('verify.doc.nameDeclared', { name: applicant.name ?? '' })}
              </span>
              <button type="button" className="shrink-0 text-sm font-medium text-primary" onClick={() => setNameDeclared(false)}>
                {t('verify.doc.nameChange')}
              </button>
            </div>
          ) : (
            <>
              <TextField
                label={t('verify.doc.nameOnLicence')}
                value={nameOnDocument}
                maxLength={100}
                autoComplete="off"
                onChange={(e) => {
                  setNameOnDocument(e.target.value);
                  setConfirming(false);
                }}
                data-testid="licence-name-input"
              />
              <p className="px-1 text-caption text-text-secondary">{t('verify.doc.nameOnLicenceHint')}</p>
              {applicant.name && (
                <button
                  type="button"
                  className="px-1 text-sm font-medium text-primary"
                  onClick={() => {
                    setNameDeclared(true);
                    setNameOnDocument('');
                    setConfirming(false);
                  }}
                  data-testid="licence-name-same"
                >
                  {t('verify.doc.nameSameAsProfile')}
                </button>
              )}
            </>
          )}
        </div>
      )}

      <div className="space-y-1">
        <TextField
          label={t(`verify.doc.number.${type}`)}
          value={number}
          maxLength={NUMBER_MAX_LENGTH[type]}
          autoCapitalize="characters"
          autoComplete="off"
          spellCheck={false}
          readOnly={Boolean(plateFromProfile)}
          onChange={(e) => setNumber(e.target.value.toUpperCase())}
          onBlur={() => setNumberTouched(true)}
          error={numberError}
          data-testid={`doc-number-${type}`}
        />
        {!numberError && (
          <p className="px-1 text-caption text-text-secondary">
            {plateFromProfile ? t('verify.doc.plateFromProfile') : t(`verify.doc.numberHint.${type}`)}
          </p>
        )}
      </div>

      {rc && (
        <fieldset className="space-y-2" data-testid="rc-owner">
          <legend className="mb-1 text-sm font-medium text-text-primary">{t('verify.doc.ownerQuestion')}</legend>
          <div className="flex flex-wrap gap-2">
            {OWNERS.map((o) => (
              <button
                key={o}
                type="button"
                aria-pressed={owner === o}
                onClick={() => setOwner(o)}
                className={`rounded-full border px-3 py-1.5 text-sm font-medium ${
                  owner === o ? 'border-primary bg-primary-light text-primary' : 'border-border text-text-secondary'
                }`}
                data-testid={`rc-owner-${o}`}
              >
                {t(`verify.doc.owner.${o}`)}
              </button>
            ))}
          </div>
          {owner && owner !== 'SELF' && (
            <div className="space-y-1">
              <TextField
                label={t('verify.doc.ownerName')}
                value={ownerName}
                maxLength={100}
                autoComplete="off"
                onChange={(e) => setOwnerName(e.target.value)}
                data-testid="rc-owner-name"
              />
              <p className="px-1 text-caption text-text-secondary">{t('verify.doc.ownerHelp')}</p>
            </div>
          )}
        </fieldset>
      )}

      {expires && (
        <div className="space-y-1">
          <TextField
            label={t('verify.doc.validUntil')}
            type="date"
            min={today}
            max={latest ?? undefined}
            value={validUntil}
            onChange={(e) => {
              setValidUntil(e.target.value);
              setConfirming(false);
            }}
            error={dateError}
            data-testid={`doc-valid-${type}`}
          />
          {!dateError && <p className="px-1 text-caption text-text-secondary">{t(`verify.doc.validHint.${type}`)}</p>}
        </div>
      )}

      {police && (
        <div className="space-y-1">
          <TextField
            label={t('verify.doc.issuedOnRequired')}
            type="date"
            min={oldestPolice}
            max={today}
            value={issuedOn}
            onChange={(e) => setIssuedOn(e.target.value)}
            error={issueError}
            data-testid="doc-issued-POLICE_CERTIFICATE"
          />
          {!issueError && !dueSoon && (
            <p className="px-1 text-caption text-text-secondary">{t('verify.doc.policeIssueHint', { date: formatDay(oldestPolice) })}</p>
          )}
          {dueSoon && dueOn && (
            <p className="rounded-input bg-accent-orange-tint px-3 py-2 text-caption text-text-primary" role="status" data-testid="police-due-soon">
              {t('verify.doc.policeDueSoon', { date: formatDay(dueOn), count: Math.max(0, dueInDays ?? 0) })}
            </p>
          )}
        </div>
      )}

      {commercialAsked && (
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

      {confirming && !blocked && (
        <div className="space-y-1 rounded-card border border-primary bg-primary-light p-3 text-sm text-text-primary" data-testid={`doc-confirm-${type}`}>
          <p className="font-medium">{t('verify.doc.confirmTitle')}</p>
          {licence && applicant.dateOfBirth && <p>{t('verify.doc.confirmDob', { date: formatDay(applicant.dateOfBirth) })}</p>}
          {validUntil && <p>{t('verify.doc.confirmValidUntil', { date: formatDay(validUntil) })}</p>}
          <p className="text-caption text-text-secondary">{t('verify.doc.confirmHint')}</p>
        </div>
      )}
      {error && <p className="text-sm text-danger" role="alert">{error}</p>}
      <div className="flex gap-2">
        <Button fullWidth disabled={blocked || sending} onClick={send} data-testid={`doc-send-${type}`}>
          {sending
            ? t('common.uploading')
            : missing ?? (dateError || issueError ? t('verify.doc.fixDate') : confirming ? t('verify.doc.confirmSend') : t('verify.doc.send'))}
        </Button>
        {confirming && !sending && (
          <Button variant="secondary" onClick={() => setConfirming(false)} data-testid={`doc-confirm-change-${type}`}>
            {t('verify.doc.confirmChange')}
          </Button>
        )}
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} disabled={sending}>
            {t('verify.doc.cancel')}
          </Button>
        )}
      </div>
    </div>
  );
}
