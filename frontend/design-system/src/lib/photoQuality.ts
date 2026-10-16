/**
 * A quick look at a photo before it is sent: is it blurry, washed out by
 * glare, too dark, or (where a face should be) missing one?
 * <p>
 * ON THE PHONE, BEFORE THE UPLOAD, so she can retake it in the moment
 * rather than hear a day later that a reviewer could not read it. It is a
 * nudge, never a gate: "Use anyway" always sends the photo, with what was
 * noticed, and the server puts it in front of a person (PHOTO_QUALITY).
 * <p>
 * Cheap on purpose: a 512 px greyscale copy and a few passes over it, no
 * model download. Thresholds are set loose, because a false warning costs
 * her a tap and a reviewer still reads every photo.
 * <p>
 * The face is checked only when the caller can find one: the partner app
 * passes its face detector, and elsewhere the browser's own FaceDetector is
 * used where the phone has it. With neither, the face is not checked, and
 * nothing is said about it.
 */

export type PhotoWarning = 'BLURRY' | 'GLARE' | 'DARK' | 'NO_FACE';

export type FaceFinder = (image: HTMLCanvasElement) => Promise<boolean | null>;

export interface PhotoCheckOptions {
  /** A face should be in it: an ID card's photo, or a selfie. */
  expectFace?: boolean;
  /** Says whether a face is there; null when it cannot tell. */
  findFace?: FaceFinder;
}

const SIZE = 512;
/** Variance of the Laplacian below which edges are too soft to read small print. */
const BLUR_LIMIT = 30;
/**
 * Glare is a patch blown out to pure white - a flash or a lamp on the
 * laminate - not a white card or bright paper, which sit a little below it.
 * The photo is cut into a 16 x 16 grid; a cell is a hotspot when most of it
 * is clipped, and glare means hotspots cover at least this share of the photo.
 * (The first version counted every near-white pixel anywhere, so a white
 * Aadhaar card on a desk read as glare.)
 */
const GLARE_GRID = 16;
const CLIPPED = 254;
const HOT_CELL_SHARE = 0.7;
const GLARE_SHARE = 0.03;
/** Mean brightness (0-255) below which print is hard to make out. */
const DARK_LIMIT = 55;

export async function checkPhoto(file: Blob, options: PhotoCheckOptions = {}): Promise<PhotoWarning[]> {
  if (!file.type.startsWith('image/')) return [];
  let canvas: HTMLCanvasElement;
  try {
    canvas = await toCanvas(file);
  } catch {
    // A format this browser cannot draw (some HEIC): the reviewer sees it as sent.
    return [];
  }
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) return [];
  const { data, width, height } = context.getImageData(0, 0, canvas.width, canvas.height);
  const grey = new Float32Array(width * height);
  let sum = 0;
  for (let i = 0, p = 0; i < data.length; i += 4, p++) {
    const y = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
    grey[p] = y;
    sum += y;
  }
  const warnings: PhotoWarning[] = [];
  const mean = sum / grey.length;
  if (mean < DARK_LIMIT) warnings.push('DARK');
  if (hotspotShare(grey, width, height) >= GLARE_SHARE) warnings.push('GLARE');
  if (laplacianVariance(grey, width, height) < BLUR_LIMIT) warnings.push('BLURRY');
  if (options.expectFace) {
    const found = await findFace(canvas, options.findFace);
    if (found === false) warnings.push('NO_FACE');
  }
  return warnings;
}

async function toCanvas(file: Blob): Promise<HTMLCanvasElement> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, SIZE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(bitmap.width * scale));
  canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close?.();
  return canvas;
}

/** Share of the photo covered by cells that are mostly clipped to white. */
export function hotspotShare(grey: Float32Array, width: number, height: number): number {
  const cellW = Math.max(1, Math.floor(width / GLARE_GRID));
  const cellH = Math.max(1, Math.floor(height / GLARE_GRID));
  let hot = 0;
  let cells = 0;
  for (let cy = 0; cy + cellH <= height; cy += cellH) {
    for (let cx = 0; cx + cellW <= width; cx += cellW) {
      let clipped = 0;
      for (let y = cy; y < cy + cellH; y++) {
        for (let x = cx; x < cx + cellW; x++) if (grey[y * width + x] >= CLIPPED) clipped++;
      }
      cells++;
      if (clipped / (cellW * cellH) >= HOT_CELL_SHARE) hot++;
    }
  }
  return cells === 0 ? 0 : hot / cells;
}

function laplacianVariance(grey: Float32Array, width: number, height: number): number {
  let sum = 0;
  let sumSq = 0;
  let n = 0;
  for (let y = 1; y < height - 1; y++) {
    for (let x = 1; x < width - 1; x++) {
      const i = y * width + x;
      const v = grey[i - width] + grey[i + width] + grey[i - 1] + grey[i + 1] - 4 * grey[i];
      sum += v;
      sumSq += v * v;
      n++;
    }
  }
  if (n === 0) return Infinity;
  const mean = sum / n;
  return sumSq / n - mean * mean;
}

type BrowserFaceDetector = { detect(image: CanvasImageSource): Promise<unknown[]> };

async function findFace(canvas: HTMLCanvasElement, given?: FaceFinder): Promise<boolean | null> {
  try {
    if (given) return await given(canvas);
    const Detector = (globalThis as unknown as { FaceDetector?: new () => BrowserFaceDetector }).FaceDetector;
    if (!Detector) return null;
    const faces = await new Detector().detect(canvas);
    return faces.length > 0;
  } catch {
    return null;
  }
}

/** As the server takes them: "BLURRY,GLARE". Empty when there is nothing to say. */
export function photoWarningsParam(warnings: PhotoWarning[] | null | undefined): string | undefined {
  return warnings && warnings.length ? warnings.join(',') : undefined;
}
