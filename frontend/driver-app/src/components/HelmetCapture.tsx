import { Camera, RotateCcw } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, HelmetIcon } from '@sheout/design-system';
import { useTranslation } from '@sheout/design-system';

type Stage = 'intro' | 'live' | 'blocked' | 'review';

/** Seconds to get the helmet on straight and look at the camera. */
const COUNTDOWN = 3;

/**
 * A photo of her with her helmet on, taken with the camera now.
 * <p>
 * Nothing reads the photo automatically - no model here can tell a helmet
 * from a hat reliably, and pretending to would be worse than not. It is
 * kept with the day's selfie for an operator to look at, and asking for it
 * every shift is what makes wearing one the habit. Camera only, the same as
 * the selfie: a gallery photo from last month proves nothing about today.
 */
export function HelmetCapture({ onCaptured }: { onCaptured: (photo: File) => void }) {
  const { t } = useTranslation();
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [stage, setStage] = useState<Stage>('intro');
  const [countdown, setCountdown] = useState(0);
  const [photo, setPhoto] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }, []);
  useEffect(() => stop, [stop]);

  useEffect(() => {
    if (!photo) return;
    const url = URL.createObjectURL(photo);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [photo]);

  async function start() {
    setPhoto(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      setStage('blocked');
      return;
    }
    try {
      streamRef.current = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 960 }, height: { ideal: 1280 } },
        audio: false,
      });
    } catch {
      setStage('blocked');
      return;
    }
    setStage('live');
    await new Promise((r) => setTimeout(r, 50));
    const video = videoRef.current;
    if (!video || !streamRef.current) return;
    video.srcObject = streamRef.current;
    await video.play().catch(() => undefined);
    for (let left = COUNTDOWN; left > 0; left--) {
      setCountdown(left);
      await new Promise((r) => setTimeout(r, 1000));
    }
    setCountdown(0);
    const width = Math.max(640, video.videoWidth);
    const scale = width / (video.videoWidth || width);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = Math.round((video.videoHeight || 480) * scale);
    canvas.getContext('2d')?.drawImage(video, 0, 0, canvas.width, canvas.height);
    stop();
    canvas.toBlob((blob) => {
      if (!blob) {
        setStage('blocked');
        return;
      }
      setPhoto(new File([blob], 'helmet.jpg', { type: 'image/jpeg' }));
      setStage('review');
    }, 'image/jpeg', 0.9);
  }

  return (
    <div className="space-y-4" data-testid="helmet-capture">
      {stage === 'intro' && (
        <>
          <div className="flex flex-col items-center gap-3 rounded-card bg-accent-orange/10 px-4 py-6 text-center">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-accent-orange/15 text-accent-orange motion-safe:animate-bob">
              <HelmetIcon className="h-9 w-9" />
            </span>
            <p className="font-heading text-card-title text-text-primary">{t('shiftCheck.helmet.title')}</p>
            <p className="text-sm text-text-secondary">{t('shiftCheck.helmet.body')}</p>
          </div>
          <Button fullWidth icon={<Camera className="h-5 w-5" />} onClick={start} data-testid="helmet-start">
            {t('shiftCheck.helmet.start')}
          </Button>
        </>
      )}

      {stage === 'live' && (
        <div className="relative mx-auto aspect-[3/4] w-full max-w-xs overflow-hidden rounded-card bg-black">
          <video ref={videoRef} playsInline muted autoPlay className="h-full w-full -scale-x-100 object-cover" />
          <p className="absolute inset-x-0 top-3 text-center text-sm font-semibold text-white drop-shadow">
            {t('shiftCheck.helmet.hold')}
          </p>
          {countdown > 0 && (
            <span className="absolute inset-0 flex items-center justify-center font-heading text-6xl font-bold text-white/90 drop-shadow motion-safe:animate-pop-in" key={countdown}>
              {countdown}
            </span>
          )}
        </div>
      )}

      {stage === 'blocked' && (
        <div className="space-y-3">
          <p className="rounded-input bg-background px-3 py-2 text-sm text-danger" role="alert">{t('shiftCheck.cameraBlocked')}</p>
          <Button fullWidth icon={<RotateCcw className="h-4 w-4" />} onClick={start}>{t('shiftCheck.tryAgain')}</Button>
        </div>
      )}

      {stage === 'review' && photo && preview && (
        <div className="space-y-3">
          <img src={preview} alt={t('shiftCheck.helmet.previewAlt')} className="mx-auto aspect-[3/4] w-40 rounded-card object-cover" data-testid="helmet-preview" />
          <div className="flex gap-3">
            <Button variant="secondary" fullWidth icon={<RotateCcw className="h-4 w-4" />} onClick={start}>
              {t('shiftCheck.retake')}
            </Button>
            <Button fullWidth onClick={() => onCaptured(photo)} data-testid="helmet-use">
              {t('shiftCheck.helmet.use')}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
