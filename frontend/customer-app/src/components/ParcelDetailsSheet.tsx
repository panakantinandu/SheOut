import { Ban, KeyRound } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Button, Overlay, TextField, useTranslation } from '@sheout/design-system';
import { parcelApi } from '../api/client';
import type { ParcelDetailsInput, ParcelRules } from '../api/types';

/** A 10-digit Indian mobile, typed with or without +91, 0, spaces or dashes - the server checks it the same way. */
export function isIndianMobile(raw: string): boolean {
  return /^(?:\+?91|0)?[6-9]\d{9}$/.test(raw.replace(/[\s-]/g, ''));
}

/**
 * The step between "Continue" and the booking: who the parcel is for, an
 * optional value (for records only - nothing is promised against it), and
 * the sender's word that it fits a bag and holds nothing on the list.
 * <p>
 * Its own sheet rather than more fields in the booking sheet, whose summary
 * must never scroll so Book stays in view - see BookingMapLayout. The rules
 * come from the server, version and all, so she always agrees to the list
 * the server will check her booking against.
 */
export function ParcelDetailsSheet({
  open,
  busy,
  error,
  onBook,
  onClose,
}: {
  open: boolean;
  busy: boolean;
  error: string | null;
  onBook: (details: ParcelDetailsInput) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [rules, setRules] = useState<ParcelRules | null>(null);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [value, setValue] = useState('');
  const [sizeOk, setSizeOk] = useState(false);
  const [noneProhibited, setNoneProhibited] = useState(false);
  const [phoneTouched, setPhoneTouched] = useState(false);

  useEffect(() => {
    if (!open || rules) return;
    parcelApi.rules().then(setRules).catch(() => {
      // Book stays off without the rules: she cannot agree to a list she has not seen.
    });
  }, [open, rules]);

  const phoneOk = isIndianMobile(phone);
  const valueNumber = value.trim() === '' ? undefined : Math.round(Number(value));
  const valueOk = valueNumber === undefined || (Number.isFinite(valueNumber) && valueNumber >= 0 && (!rules || valueNumber <= rules.maxDeclaredValue));
  const ready = !!rules && name.trim().length > 0 && phoneOk && valueOk && sizeOk && noneProhibited && !busy;

  return (
    <Overlay open={open} label={t('delivery.parcelSheet.title')} onDismiss={busy ? undefined : onClose} align="sheet">
      <div className="flex max-h-[92vh] w-full flex-col rounded-t-card bg-surface shadow-lift" data-testid="parcel-details-sheet">
        <div className="overflow-y-auto overscroll-contain px-screen pb-4 pt-5">
          <h2 className="font-heading text-title text-text-primary">{t('delivery.parcelSheet.title')}</h2>

          <div className="mt-4 space-y-4">
            <TextField
              label={t('delivery.parcelSheet.receiverName')}
              placeholder={t('delivery.parcelSheet.receiverNamePlaceholder')}
              maxLength={80}
              autoComplete="off"
              value={name}
              onChange={(e) => setName(e.target.value)}
              data-testid="receiver-name"
            />
            <div>
              <TextField
                label={t('delivery.parcelSheet.receiverPhone')}
                type="tel"
                inputMode="tel"
                maxLength={16}
                autoComplete="off"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                onBlur={() => setPhoneTouched(true)}
                data-testid="receiver-phone"
              />
              <p className={`mt-1 text-caption ${phoneTouched && phone && !phoneOk ? 'text-danger' : 'text-text-secondary'}`}>
                {phoneTouched && phone && !phoneOk ? t('delivery.parcelSheet.receiverPhoneInvalid') : t('delivery.parcelSheet.receiverPhoneHint')}
              </p>
            </div>
            <div>
              <TextField
                label={t('delivery.parcelSheet.declaredValue')}
                type="number"
                inputMode="numeric"
                min={0}
                step={1}
                value={value}
                onChange={(e) => setValue(e.target.value.replace(/[^\d]/g, ''))}
                data-testid="declared-value"
              />
              <p className={`mt-1 text-caption ${valueOk ? 'text-text-secondary' : 'text-danger'}`}>
                {valueOk
                  ? t('delivery.parcelSheet.declaredValueHint', { max: rules?.maxDeclaredValue ?? 5000 })
                  : t('delivery.parcelSheet.declaredValueTooHigh', { max: rules?.maxDeclaredValue ?? 5000 })}
              </p>
            </div>

            <label className="flex min-h-tap cursor-pointer items-start gap-3 rounded-input border border-border px-3 py-3">
              <input type="checkbox" className="mt-0.5 h-5 w-5 shrink-0 accent-primary" checked={sizeOk} onChange={(e) => setSizeOk(e.target.checked)} data-testid="size-ack" />
              <span className="text-sm text-text-primary">{t('delivery.parcelSheet.sizeAck', { kg: rules?.maxWeightKg ?? 5 })}</span>
            </label>

            <div className="rounded-input bg-background p-3" data-testid="prohibited-list">
              <p className="flex items-center gap-2 text-sm font-semibold text-text-primary">
                <Ban className="h-4 w-4 text-danger" aria-hidden="true" />
                {t('delivery.parcelSheet.prohibitedTitle')}
              </p>
              <ul className="mt-2 grid grid-cols-1 gap-x-3 gap-y-1 text-sm text-text-secondary min-[400px]:grid-cols-2">
                {(rules?.prohibitedItems ?? []).map((code) => (
                  <li key={code} className="flex gap-1.5"><span aria-hidden="true">•</span>{t(`delivery.prohibited.${code}`)}</li>
                ))}
              </ul>
              <label className="mt-3 flex min-h-tap cursor-pointer items-start gap-3">
                <input type="checkbox" className="mt-0.5 h-5 w-5 shrink-0 accent-primary" checked={noneProhibited} onChange={(e) => setNoneProhibited(e.target.checked)} data-testid="prohibited-ack" />
                <span className="text-sm font-medium text-text-primary">{t('delivery.parcelSheet.prohibitedAck')}</span>
              </label>
            </div>

            <p className="flex items-start gap-2 text-sm text-text-secondary">
              <KeyRound className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
              {t('delivery.parcelSheet.codeHint')}
            </p>
          </div>
        </div>

        <div className="space-y-2 border-t border-border px-screen pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
          {error && <p className="text-sm text-danger" role="alert">{error}</p>}
          <Button
            fullWidth
            disabled={!ready}
            onClick={() => rules && onBook({
              receiverName: name.trim(),
              receiverPhone: phone.trim(),
              declaredValue: valueNumber,
              prohibitedItemsVersion: rules.prohibitedItemsVersion,
              prohibitedItemsAcknowledged: noneProhibited,
              sizeAcknowledged: sizeOk,
            })}
            data-testid="parcel-book"
          >
            {busy ? t('booking.booking') : t('delivery.parcelSheet.book')}
          </Button>
          <Button fullWidth variant="secondary" size="md" disabled={busy} onClick={onClose}>
            {t('delivery.parcelSheet.back')}
          </Button>
        </div>
      </div>
    </Overlay>
  );
}
