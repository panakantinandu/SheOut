import { Eraser, Undo2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from './Button';

/** A rectangle she has drawn over something she does not want us to see. */
interface Mask {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface DocumentMaskerProps {
  /** The image she chose. */
  file: File;
  /** The masked image, as a file ready to upload under the same name. */
  onDone: (masked: File) => void;
  onCancel: () => void;
  busy?: boolean;
}

/** Below this, a drag was a tap: ignored rather than left as a speck on her ID. */
const MIN_DRAG_PX = 6;

/**
 * Covering the ID number before it is sent.
 * <p>
 * WHAT IS ACTUALLY NEEDED HERE. A rider is verified as a woman by a person
 * looking at a name, a face and a gender marker. The Aadhaar number itself
 * is not part of that decision, and holding one is a liability to her far
 * more than an asset to us: it is the number that unlocks her bank, her SIM
 * and her benefits. So she is invited to cover it, and what we store is what
 * she chose to show us. Partners are a different case and keep the full
 * document - they carry riders, and that trust is checked further, including
 * a police record.
 * <p>
 * SOLID BLOCKS, NOT A BLUR. A blur is reversible to anyone who cares enough,
 * and looks like protection while being none. The pixels under these
 * rectangles are painted over before the file is made, so the number is not
 * in the bytes that leave her phone - not hidden behind a layer, gone. The
 * original is never uploaded.
 * <p>
 * Drawn on a canvas sized to the image's own pixels, so a mask lands where
 * she drew it whatever the screen's size or density.
 */
/**
 * The longest side, in pixels, of the photo she masks and sends. Plenty to
 * read a name and a face; a phone camera's 4000 x 3000 original is too big to
 * repaint on every finger movement on a phone (and over some phones' canvas
 * limit), which looked like "Use anyway" doing nothing.
 */
const WORKING_SIDE = 2000;

export function DocumentMasker({ file, onDone, onCancel, busy = false }: DocumentMaskerProps) {
  const { t } = useTranslation('ds');
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const boxRef = useRef<HTMLDivElement>(null);
  const imageRef = useRef<HTMLImageElement | null>(null);
  const [masks, setMasks] = useState<Mask[]>([]);
  const [drawing, setDrawing] = useState<Mask | null>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      imageRef.current = image;
      setReady(true);
      // Opened from a notice further up the screen: make sure she sees it.
      requestAnimationFrame(() => boxRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    };
    image.onerror = () => setError(t('masker.cannotOpen'));
    image.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file, t]);

  // Repainted on every change: the photo, then every rectangle over it.
  useEffect(() => {
    const canvas = canvasRef.current;
    const image = imageRef.current;
    if (!canvas || !image || !ready) return;
    const scale = Math.min(1, WORKING_SIDE / Math.max(image.naturalWidth, image.naturalHeight));
    const width = Math.max(1, Math.round(image.naturalWidth * scale));
    const height = Math.max(1, Math.round(image.naturalHeight * scale));
    if (canvas.width !== width) canvas.width = width;
    if (canvas.height !== height) canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return;
    context.drawImage(image, 0, 0, width, height);
    context.fillStyle = '#111111';
    for (const mask of [...masks, ...(drawing ? [drawing] : [])]) {
      context.fillRect(mask.x, mask.y, mask.width, mask.height);
    }
  }, [masks, drawing, ready]);

  /** Where a touch or a click landed, in the image's own pixels. */
  function pointIn(canvas: HTMLCanvasElement, clientX: number, clientY: number) {
    const box = canvas.getBoundingClientRect();
    return {
      x: ((clientX - box.left) / box.width) * canvas.width,
      y: ((clientY - box.top) / box.height) * canvas.height,
    };
  }

  function startDrag(clientX: number, clientY: number) {
    const canvas = canvasRef.current;
    if (!canvas || busy) return;
    const point = pointIn(canvas, clientX, clientY);
    setDrawing({ x: point.x, y: point.y, width: 0, height: 0 });
  }

  function moveDrag(clientX: number, clientY: number) {
    const canvas = canvasRef.current;
    if (!canvas || !drawing) return;
    const point = pointIn(canvas, clientX, clientY);
    setDrawing({
      x: Math.min(drawing.x, point.x),
      y: Math.min(drawing.y, point.y),
      width: Math.abs(point.x - drawing.x),
      height: Math.abs(point.y - drawing.y),
    });
  }

  function endDrag() {
    if (!drawing) return;
    const canvas = canvasRef.current;
    const scale = canvas ? canvas.width / (canvas.getBoundingClientRect().width || 1) : 1;
    if (drawing.width > MIN_DRAG_PX * scale && drawing.height > MIN_DRAG_PX * scale) {
      setMasks((current) => [...current, drawing]);
    }
    setDrawing(null);
  }

  /**
   * Makes the file that will actually be uploaded.
   * <p>
   * JPEG at high quality: it is a photograph of a card, the original was
   * almost certainly one already, and it keeps a phone camera's 6MB down to
   * something that uploads on a patchy connection. The name is kept so an
   * operator sees a filename that means something.
   */
  function finish() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          setError(t('masker.cannotSave'));
          return;
        }
        const name = file.name.replace(/\.[^.]+$/, '') + '.jpg';
        onDone(new File([blob], name, { type: 'image/jpeg' }));
      },
      'image/jpeg',
      // High quality on purpose: a person has to read a name and a face off
      // this, and the file is re-encoded once here and never again.
      0.95
    );
  }

  return (
    <div ref={boxRef} className="scroll-mt-4 space-y-3" data-testid="document-masker">
      <p className="text-sm font-semibold text-text-primary">{t('masker.title')}</p>
      <p className="text-xs leading-relaxed text-text-secondary">{t('masker.instruction')}</p>

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="overflow-hidden rounded-card border border-border bg-background">
        <canvas
          ref={canvasRef}
          data-testid="masker-canvas"
          className="block w-full touch-none select-none"
          onPointerDown={(e) => {
            e.currentTarget.setPointerCapture(e.pointerId);
            startDrag(e.clientX, e.clientY);
          }}
          onPointerMove={(e) => moveDrag(e.clientX, e.clientY)}
          onPointerUp={endDrag}
          onPointerCancel={endDrag}
        />
      </div>

      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-text-secondary" data-testid="masker-count">
          {masks.length === 0 ? t('masker.none') : t('masker.count', { count: masks.length })}
        </p>
        <div className="flex gap-2">
          <Button
            size="md"
            variant="secondary"
            disabled={masks.length === 0 || busy}
            icon={<Undo2 className="h-4 w-4" />}
            onClick={() => setMasks((current) => current.slice(0, -1))}
            data-testid="masker-undo"
          >
            {t('masker.undo')}
          </Button>
          <Button
            size="md"
            variant="secondary"
            disabled={masks.length === 0 || busy}
            icon={<Eraser className="h-4 w-4" />}
            onClick={() => setMasks([])}
          >
            {t('masker.clear')}
          </Button>
        </div>
      </div>

      <div className="flex gap-3">
        <Button variant="secondary" fullWidth disabled={busy} onClick={onCancel} data-testid="masker-cancel">
          {t('common.cancel')}
        </Button>
        {/* Usable with no masks drawn: covering the number is hers to decide,
            and a tool that refuses to continue until she uses it is not an
            invitation. */}
        <Button fullWidth disabled={!ready || busy} onClick={finish} data-testid="masker-done">
          {t('masker.useThis')}
        </Button>
      </div>
    </div>
  );
}
