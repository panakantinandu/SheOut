import { Camera, CheckCircle2 } from 'lucide-react';
import { useRef, useState } from 'react';
import { Button, useTranslation } from '@sheout/design-system';
import { parcelApi } from '../api/client';
import type { ParcelPhotoKind, ParcelView } from '../api/types';
import { prepareParcelPhoto } from '../lib/documentPhoto';

/**
 * One parcel photo, from the camera. capture="environment" opens the back
 * camera directly on Android and iOS, with no gallery to pick an old photo
 * from. The server stamps its own time and her position on it, and flags the
 * same picture used on another delivery - so a photo from earlier is caught
 * even on a phone that offers the gallery anyway.
 * <p>
 * The hint says what to photograph and what not to: the parcel, never a face.
 */
export function ParcelPhotoCapture({ bookingId, kind, taken, onSaved }: {
  bookingId: string;
  kind: ParcelPhotoKind;
  taken: boolean;
  onSaved: (parcel: ParcelView) => void;
}) {
  const { t } = useTranslation();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onFile(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      onSaved(await parcelApi.uploadPhoto(bookingId, kind, await prepareParcelPhoto(file)));
    } catch {
      setError(t('trip.parcel.photoError'));
    } finally {
      setBusy(false);
      if (input.current) input.current.value = '';
    }
  }

  const picker = (
    <input
      ref={input}
      type="file"
      accept="image/*"
      capture="environment"
      className="hidden"
      onChange={(e) => onFile(e.target.files?.[0])}
      data-testid={`parcel-photo-input-${kind.toLowerCase()}`}
    />
  );

  // Taken: one line, so the code and "Mark delivered" come up into view.
  if (taken && !busy && !error) {
    return (
      <div className="flex items-center justify-between gap-2 rounded-input bg-accent-green/10 px-3 py-1" data-testid={`parcel-photo-${kind.toLowerCase()}`}>
        <p className="flex min-w-0 items-center gap-2 text-sm font-semibold text-accent-green-strong">
          <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" />
          <span className="truncate">{t('trip.parcel.photoSaved')}</span>
        </p>
        {picker}
        <button
          type="button"
          className="min-h-tap shrink-0 px-2 text-sm font-semibold text-primary"
          onClick={() => input.current?.click()}
          data-testid={`parcel-photo-take-${kind.toLowerCase()}`}
        >
          {t('trip.parcel.retakePhoto')}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-2 rounded-input border border-border p-3" data-testid={`parcel-photo-${kind.toLowerCase()}`}>
      <p className="flex items-center gap-2 text-sm font-semibold text-text-primary">
        {taken ? <CheckCircle2 className="h-4 w-4 text-accent-green-strong" aria-hidden="true" /> : <Camera className="h-4 w-4 text-primary" aria-hidden="true" />}
        {taken ? t('trip.parcel.photoSaved') : t(`trip.parcel.photoTitle${kind}`)}
      </p>
      <p className="text-caption text-text-secondary">{t(`trip.parcel.photoHint${kind}`)}</p>
      {picker}
      <Button
        fullWidth
        size="md"
        variant={taken ? 'secondary' : 'primary'}
        icon={<Camera className="h-4 w-4" />}
        disabled={busy}
        onClick={() => input.current?.click()}
        data-testid={`parcel-photo-take-${kind.toLowerCase()}`}
      >
        {busy ? t('trip.parcel.uploading') : taken ? t('trip.parcel.retakePhoto') : t('trip.parcel.takePhoto')}
      </Button>
      {error && <p className="text-sm text-danger" role="alert">{error}</p>}
    </div>
  );
}
