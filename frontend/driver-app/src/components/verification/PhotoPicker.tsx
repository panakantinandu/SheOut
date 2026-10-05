import { Camera, FileText, Upload } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Button, useTranslation } from '@sheout/design-system';

/**
 * Her photo of a document: the camera straight away, or a file she already
 * has - and once chosen, the picture itself, so she can see it is the right
 * paper and readable before she sends it.
 * <p>
 * Two buttons rather than one file input: on a phone, "choose a file" opens
 * a gallery or a file manager, and a partner standing next to her scooter
 * with the RC in her hand wants the camera. capture="environment" asks for
 * the back camera.
 */
export function PhotoPicker({
  file,
  onPick,
  disabled,
  testId,
}: {
  file: File | null;
  onPick: (file: File) => void;
  disabled?: boolean;
  testId?: string;
}) {
  const { t } = useTranslation();
  const camera = useRef<HTMLInputElement>(null);
  const files = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);

  useEffect(() => {
    if (!file || !file.type.startsWith('image/')) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  function picked(e: React.ChangeEvent<HTMLInputElement>) {
    const chosen = e.target.files?.[0];
    // Cleared so picking the same file again still fires.
    e.target.value = '';
    if (chosen) onPick(chosen);
  }

  return (
    <div className="space-y-2" data-testid={testId}>
      <input ref={camera} type="file" accept="image/*" capture="environment" className="hidden" onChange={picked} />
      <input ref={files} type="file" accept="image/*,.pdf" className="hidden" onChange={picked} data-testid={testId ? `${testId}-input` : undefined} />

      {file ? (
        <div className="flex items-center gap-3 rounded-card border border-border bg-surface p-2">
          {preview ? (
            <img src={preview} alt="" className="h-16 w-24 shrink-0 rounded-input object-cover" />
          ) : (
            <span className="flex h-16 w-24 shrink-0 flex-col items-center justify-center rounded-input bg-background text-text-secondary">
              <FileText className="h-6 w-6" aria-hidden="true" />
              <span className="text-caption">{t('verify.doc.pdf')}</span>
            </span>
          )}
          <span className="min-w-0 flex-1 truncate text-sm text-text-primary">{file.name}</span>
          <span className="flex shrink-0 items-center gap-1">
            <button
              type="button"
              aria-label={t('verify.doc.takePhoto')}
              className="rounded-full p-2 text-primary"
              disabled={disabled}
              onClick={() => camera.current?.click()}
            >
              <Camera className="h-5 w-5" aria-hidden="true" />
            </button>
            <button
              type="button"
              aria-label={t('verify.doc.chooseFile')}
              className="rounded-full p-2 text-primary"
              disabled={disabled}
              onClick={() => files.current?.click()}
            >
              <Upload className="h-5 w-5" aria-hidden="true" />
            </button>
          </span>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-2">
          <Button size="md" icon={<Camera className="h-4 w-4" />} disabled={disabled} onClick={() => camera.current?.click()}>
            {t('verify.doc.takePhoto')}
          </Button>
          <Button size="md" variant="secondary" icon={<Upload className="h-4 w-4" />} disabled={disabled} onClick={() => files.current?.click()}>
            {t('verify.doc.chooseFile')}
          </Button>
        </div>
      )}
    </div>
  );
}
