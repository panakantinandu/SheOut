import { Camera, RotateCcw, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';

/** What the server asked her to do, in its order - see SelfiePrompt on the backend. */
export type SelfiePrompt = 'TURN_LEFT' | 'TURN_RIGHT' | 'LOOK_UP' | 'SMILE' | 'CLOSE_EYES';

export interface SelfieChallenge {
  challengeId: string;
  prompts: SelfiePrompt[];
}

export interface LiveSelfieResult {
  /** The facing-the-camera frame. */
  selfie: File;
  /** One image: the frame taken during each prompt, left to right, in the order asked. */
  livenessFrames: File;
  challengeId: string;
}

export interface LiveSelfieCaptureProps {
  /** Asks the server for this attempt's prompts. Called on every start and retake. */
  requestChallenge: () => Promise<SelfieChallenge>;
  onCaptured: (result: LiveSelfieResult) => void;
  /** A retake asks the server for new prompts, which voids the selfie taken before - the screen must drop it. */
  onReset?: () => void;
  /** Set once a selfie has been accepted, so the step shows it as done. */
  captured?: LiveSelfieResult | null;
  busy?: boolean;
}

type Stage = 'intro' | 'starting' | 'blocked' | 'prompting' | 'noMotion' | 'review';

/** How long each prompt is held before its frame is taken. */
const HOLD_MS = 3000;
/** Frames are taken at least this wide - the server refuses a photo under 480x320. */
const MIN_WIDTH = 640;
/**
 * Mean change per pixel (0-255, on a small greyscale copy) a prompt frame
 * must show against the first frame. A person turning her head or closing
 * her eyes moves far more than this; a photo held still, or a frozen feed,
 * barely moves at all. A nudge, not a test - the reviewer is the check.
 */
const MIN_MOVEMENT = 6;

type Step = { key: SelfiePrompt | 'STRAIGHT' | 'STRAIGHT_AGAIN' };

/**
 * A live selfie, taken here and now with the phone's own camera.
 * <p>
 * CAMERA ONLY, ON PURPOSE. There is no file picker in this component and
 * never should be: an upload proves only that somebody had a photo, and the
 * point of this step is that somebody was physically in front of the phone
 * when it was made. If the camera cannot be opened, the step says why and
 * how to fix it - it does not fall back to choosing a picture.
 * <p>
 * PROMPTS FROM THE SERVER. Before the final frame she is asked to do two
 * things - turn her head, close her eyes, smile - chosen by the server at
 * random for this attempt, and a frame is taken during each. They go up
 * together as one image, and the reviewer sees each frame captioned with
 * what was actually asked. A photo held up to the camera cannot follow them.
 * <p>
 * NOTHING HERE DECIDES ANYTHING. There is no face matching and no score.
 * The movement check only catches a feed that did not change at all and
 * asks her to try again; a person compares the selfie with the ID.
 */
export function LiveSelfieCapture({ requestChallenge, onCaptured, onReset, captured = null, busy = false }: LiveSelfieCaptureProps) {
  const { t } = useTranslation('ds');
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const cancelledRef = useRef(false);
  const [stage, setStage] = useState<Stage>(captured ? 'review' : 'intro');
  const [blockedReason, setBlockedReason] = useState<string | null>(null);
  const [instruction, setInstruction] = useState<string>('');
  const [countdown, setCountdown] = useState<number>(0);
  const [result, setResult] = useState<LiveSelfieResult | null>(captured);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }, []);

  useEffect(() => () => {
    cancelledRef.current = true;
    stopCamera();
  }, [stopCamera]);

  useEffect(() => {
    if (!result) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(result.selfie);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [result]);

  function promptText(step: Step['key']): string {
    switch (step) {
      case 'STRAIGHT': return t('selfie.prompt.straight');
      case 'STRAIGHT_AGAIN': return t('selfie.prompt.straightAgain');
      case 'TURN_LEFT': return t('selfie.prompt.turnLeft');
      case 'TURN_RIGHT': return t('selfie.prompt.turnRight');
      case 'LOOK_UP': return t('selfie.prompt.lookUp');
      case 'SMILE': return t('selfie.prompt.smile');
      case 'CLOSE_EYES': return t('selfie.prompt.closeEyes');
    }
  }

  /** The current video frame, unmirrored, at least MIN_WIDTH wide. */
  function grab(): HTMLCanvasElement | null {
    const video = videoRef.current;
    if (!video || !video.videoWidth) return null;
    const scale = Math.max(1, MIN_WIDTH / video.videoWidth);
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(video.videoWidth * scale);
    canvas.height = Math.round(video.videoHeight * scale);
    canvas.getContext('2d')?.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas;
  }

  /** A 32x24 greyscale copy, for the movement check. */
  function thumbnail(frame: HTMLCanvasElement): Uint8ClampedArray {
    const small = document.createElement('canvas');
    small.width = 32;
    small.height = 24;
    const context = small.getContext('2d');
    if (!context) return new Uint8ClampedArray();
    context.drawImage(frame, 0, 0, 32, 24);
    const rgba = context.getImageData(0, 0, 32, 24).data;
    const grey = new Uint8ClampedArray(32 * 24);
    for (let i = 0; i < grey.length; i++) {
      grey[i] = (rgba[i * 4] * 0.299 + rgba[i * 4 + 1] * 0.587 + rgba[i * 4 + 2] * 0.114);
    }
    return grey;
  }

  function movement(a: Uint8ClampedArray, b: Uint8ClampedArray): number {
    if (!a.length || a.length !== b.length) return 0;
    let total = 0;
    for (let i = 0; i < a.length; i++) total += Math.abs(a[i] - b[i]);
    return total / a.length;
  }

  function toFile(canvas: HTMLCanvasElement, name: string): Promise<File> {
    return new Promise((resolve, reject) => {
      canvas.toBlob(
        (blob) => (blob ? resolve(new File([blob], name, { type: 'image/jpeg' })) : reject(new Error('encode'))),
        'image/jpeg',
        0.92
      );
    });
  }

  /** The prompt frames side by side, in the order asked, each at the same height. */
  function strip(frames: HTMLCanvasElement[]): HTMLCanvasElement {
    const height = 480;
    const widths = frames.map((f) => Math.round((f.width / f.height) * height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(MIN_WIDTH, widths.reduce((sum, w) => sum + w, 0));
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (context) {
      context.fillStyle = '#000000';
      context.fillRect(0, 0, canvas.width, canvas.height);
      let x = 0;
      frames.forEach((frame, i) => {
        context.drawImage(frame, x, 0, widths[i], height);
        x += widths[i];
      });
    }
    return canvas;
  }

  const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

  /** Holds one prompt on screen with a countdown, then takes its frame. */
  async function hold(step: Step['key']): Promise<HTMLCanvasElement | null> {
    setInstruction(promptText(step));
    for (let left = Math.ceil(HOLD_MS / 1000); left > 0; left--) {
      if (cancelledRef.current) return null;
      setCountdown(left);
      await wait(1000);
    }
    setCountdown(0);
    return grab();
  }

  async function start() {
    cancelledRef.current = false;
    setBlockedReason(null);
    setResult(null);
    onReset?.();
    setStage('starting');

    // Not an HTTPS page, or a browser without a camera API. Said plainly;
    // there is no upload to fall back to.
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      setBlockedReason(t('selfie.unsupported'));
      setStage('blocked');
      return;
    }

    let challenge: SelfieChallenge;
    try {
      challenge = await requestChallenge();
    } catch (err) {
      setBlockedReason(err instanceof Error && err.message ? err.message : t('selfie.challengeError'));
      setStage('blocked');
      return;
    }

    try {
      streamRef.current = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 960 }, height: { ideal: 1280 } },
        audio: false,
      });
    } catch (err) {
      const name = err instanceof DOMException ? err.name : '';
      setBlockedReason(
        name === 'NotAllowedError' || name === 'SecurityError' ? t('selfie.denied')
          : name === 'NotFoundError' || name === 'OverconstrainedError' ? t('selfie.noCamera')
            : t('selfie.cameraBusy')
      );
      setStage('blocked');
      return;
    }

    setStage('prompting');
    // The video element renders once the stage is 'prompting'.
    await wait(50);
    const video = videoRef.current;
    if (!video || !streamRef.current) return;
    video.srcObject = streamRef.current;
    await video.play().catch(() => undefined);
    // Give the sensor a moment to settle its exposure.
    await wait(800);

    const baseline = await hold('STRAIGHT');
    const promptFrames: HTMLCanvasElement[] = [];
    for (const prompt of challenge.prompts) {
      const frame = await hold(prompt);
      if (!frame) break;
      promptFrames.push(frame);
    }
    const finalFrame = await hold('STRAIGHT_AGAIN');
    stopCamera();
    if (cancelledRef.current) return;

    if (!baseline || !finalFrame || promptFrames.length !== challenge.prompts.length) {
      setBlockedReason(t('selfie.cameraBusy'));
      setStage('blocked');
      return;
    }

    const base = thumbnail(baseline);
    const moved = Math.max(...promptFrames.map((frame) => movement(base, thumbnail(frame))));
    if (moved < MIN_MOVEMENT) {
      setStage('noMotion');
      return;
    }

    try {
      const captured: LiveSelfieResult = {
        selfie: await toFile(finalFrame, 'selfie.jpg'),
        livenessFrames: await toFile(strip(promptFrames), 'liveness.jpg'),
        challengeId: challenge.challengeId,
      };
      setResult(captured);
      setStage('review');
    } catch {
      setBlockedReason(t('selfie.cameraBusy'));
      setStage('blocked');
    }
  }

  function cancel() {
    cancelledRef.current = true;
    stopCamera();
    setStage(result ? 'review' : 'intro');
  }

  return (
    <div className="space-y-3" data-testid="live-selfie">
      <div className="flex items-start gap-2">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
        <p className="text-xs leading-relaxed text-text-secondary">{t('selfie.why')}</p>
      </div>

      {stage === 'intro' && (
        <Button fullWidth icon={<Camera className="h-4 w-4" />} disabled={busy} onClick={start} data-testid="selfie-start">
          {t('selfie.start')}
        </Button>
      )}

      {stage === 'starting' && <p className="text-sm text-text-secondary" role="status">{t('selfie.opening')}</p>}

      {stage === 'prompting' && (
        <div className="space-y-3">
          <div className="relative mx-auto aspect-[3/4] w-full max-w-xs overflow-hidden rounded-card bg-black">
            {/* Mirrored on screen, as every front camera is; the frames taken are not. */}
            <video
              ref={videoRef}
              playsInline
              muted
              autoPlay
              className="h-full w-full -scale-x-100 object-cover"
              data-testid="selfie-video"
            />
            <div className="pointer-events-none absolute inset-[12%] rounded-[50%] border-4 border-white/70" aria-hidden="true" />
            {countdown > 0 && (
              <span className="absolute right-3 top-3 flex h-10 w-10 items-center justify-center rounded-full bg-black/60 font-heading text-lg font-semibold text-white">
                {countdown}
              </span>
            )}
          </div>
          <p className="text-center font-heading text-base font-semibold text-text-primary" aria-live="assertive" data-testid="selfie-instruction">
            {instruction}
          </p>
          <Button fullWidth variant="secondary" onClick={cancel}>{t('common.cancel')}</Button>
        </div>
      )}

      {stage === 'blocked' && (
        <div className="space-y-3">
          <p className="rounded-input bg-background px-3 py-2 text-sm text-danger" role="alert" data-testid="selfie-blocked">
            {blockedReason}
          </p>
          <Button fullWidth icon={<RotateCcw className="h-4 w-4" />} onClick={start}>{t('selfie.tryAgain')}</Button>
        </div>
      )}

      {stage === 'noMotion' && (
        <div className="space-y-3">
          <p className="rounded-input bg-background px-3 py-2 text-sm text-text-primary" role="alert">{t('selfie.noMotion')}</p>
          <Button fullWidth icon={<RotateCcw className="h-4 w-4" />} onClick={start}>{t('selfie.tryAgain')}</Button>
        </div>
      )}

      {stage === 'review' && result && previewUrl && (
        <div className="space-y-3">
          <img
            src={previewUrl}
            alt={t('selfie.previewAlt')}
            className="mx-auto aspect-[3/4] w-40 rounded-card object-cover"
            data-testid="selfie-preview"
          />
          <p className="text-center text-sm font-medium text-text-primary">{t('selfie.done')}</p>
          <div className="flex gap-3">
            <Button variant="secondary" fullWidth disabled={busy} icon={<RotateCcw className="h-4 w-4" />} onClick={start}>
              {t('selfie.retake')}
            </Button>
            {result !== captured && (
              <Button fullWidth disabled={busy} onClick={() => onCaptured(result)} data-testid="selfie-use">
                {t('selfie.useThis')}
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
