import { useEffect, useRef, useState } from 'react';
import { Camera, FileText, Upload } from 'lucide-react';
import { Button, PhotoQualityNotice, checkPhoto, useTranslation, type PhotoWarning } from '@sheout/design-system';
import { findFaceIn } from '../../lib/faceMatch';

/**
 * Her photo of a document: the camera straight away, or a file she already
 * has - and once chosen, the picture itself, so she can see it is the right
 * paper and readable before she sends it.
 * <p>
 * Two buttons rather than one file input: on a phone, "choose a file" opens
 * a gallery or a file manager, and a partner standing next to her scooter
 * with the RC in her hand wants the camera. capture="environment" asks for
 * the back camera.
 * <p>
 * A QUICK LOOK FIRST. A photo is checked on the phone for blur, glare and
 * darkness (and, with expectFace, for the face on the card) before it is
 * taken. If something is off she is told what and why, and chooses: retake,
 * or use it anyway. Either way the choice is hers; "use anyway" passes the
 * warnings along, so the reviewer knows to look closer.
 */
export function PhotoPicker({
  file,
  onPick,
  disabled,
  testId,
  expectFace,
}: {
  file: File | null;
  /** warnings: what the check noticed and she sent anyway; empty when nothing. */
  onPick: (file: File, warnings: PhotoWarning[]) => void;
  disabled?: boolean;
  testId?: string;
  /** A face should be on it - an ID card's photo. */
  expectFace?: boolean;
}) {
  const { t } = useTranslation();
  const camera = useRef<HTMLInputElement>(null);
  const files = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  /** A photo the check had something to say about, waiting for her choice. */
  const [doubtful, setDoubtful] = useState<{ file: File; warnings: PhotoWarning[] } | null>(null);
  const [checking, setChecking] = useState(false);
  const shown = doubtful?.file ?? file;

  useEffect(() => {
    if (!shown || !shown.type.startsWith('image/')) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(shown);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [shown]);

  async function picked(e: React.ChangeEvent<HTMLInputElement>) {
    const chosen = e.target.files?.[0];
    // Cleared so picking the same file again still fires.
    e.target.value = '';
    if (!chosen) return;
    setChecking(true);
    const warnings = await checkPhoto(chosen, { expectFace, findFace: expectFace ? findFaceIn : undefined });
    setChecking(false);
    if (warnings.length) {
      setDoubtful({ file: chosen, warnings });
      return;
    }
    setDoubtful(null);
    onPick(chosen, []);
  }

  return (
    <div className="space-y-2" data-testid={testId}>
      <input ref={camera} type="file" accept="image/*" capture="environment" className="hidden" onChange={picked} />
      <input ref={files} type="file" accept="image/*,.pdf" className="hidden" onChange={picked} data-testid={testId ? `${testId}-input` : undefined} />

      {shown ? (
        <div className="flex items-center gap-3 rounded-card border border-border bg-surface p-2">
          {preview ? (
            <img src={preview} alt="" className="h-16 w-24 shrink-0 rounded-input object-cover" />
          ) : (
            <span className="flex h-16 w-24 shrink-0 flex-col items-center justify-center rounded-input bg-background text-text-secondary">
              <FileText className="h-6 w-6" aria-hidden="true" />
              <span className="text-caption">{t('verify.doc.pdf')}</span>
            </span>
          )}
          <span className="min-w-0 flex-1 truncate text-sm text-text-primary">{shown.name}</span>
          <span className="flex shrink-0 items-center gap-1">
            <button
              type="button"
              aria-label={t('verify.doc.takePhoto')}
              className="rounded-full p-2 text-primary"
              disabled={disabled || checking}
              onClick={() => camera.current?.click()}
            >
              <Camera className="h-5 w-5" aria-hidden="true" />
            </button>
            <button
              type="button"
              aria-label={t('verify.doc.chooseFile')}
              className="rounded-full p-2 text-primary"
              disabled={disabled || checking}
              onClick={() => files.current?.click()}
            >
              <Upload className="h-5 w-5" aria-hidden="true" />
            </button>
          </span>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-2">
          <Button size="md" icon={<Camera className="h-4 w-4" />} disabled={disabled || checking} onClick={() => camera.current?.click()}>
            {t('verify.doc.takePhoto')}
          </Button>
          <Button size="md" variant="secondary" icon={<Upload className="h-4 w-4" />} disabled={disabled || checking} onClick={() => files.current?.click()}>
            {t('verify.doc.chooseFile')}
          </Button>
        </div>
      )}

      {checking && <p className="px-1 text-caption text-text-secondary" role="status">{t('verify.doc.checkingPhoto')}</p>}
      {doubtful && (
        <PhotoQualityNotice
          warnings={doubtful.warnings}
          disabled={disabled}
          onRetake={() => camera.current?.click()}
          onUseAnyway={() => {
            onPick(doubtful.file, doubtful.warnings);
            setDoubtful(null);
          }}
          testId={testId ? `${testId}-quality` : undefined}
        />
      )}
    </div>
  );
}
