/**
 * Compares her start-of-shift selfie with the selfie she was verified with,
 * on this phone.
 * <p>
 * ON THE PHONE, BECAUSE THAT IS WHERE IT CAN RUN. The comparison needs a
 * neural network; the server is a small Java service that should not grow
 * one. The phone turns each face into 128 numbers and sends only how far
 * apart the two sets are - the server decides what that distance means
 * (ShiftCheckService), and keeps the photo so a person can look.
 * <p>
 * The library and its weights (about 1.3 MB of code, 7 MB of weights) load
 * only when the selfie screen opens, and the weights are kept by the
 * service worker after the first time - see vite.config.ts.
 * <p>
 * WHAT IT DOES NOT DO: guess whether a face is a woman's. The package ships
 * a gender model and it is not loaded. Those guesses are unreliable, worst
 * for exactly the women least like the photos they were trained on, and a
 * wrong one would lock a real partner out of work. Whether she is a woman
 * was decided by a person reading her ID; this checks she is that person.
 */

type FaceApi = typeof import('@vladmandic/face-api');

export type FaceComparison =
  | { outcome: 'COMPARED'; distance: number }
  /** No face found in the live selfie - retake, nothing is sent. */
  | { outcome: 'NO_FACE' }
  /** The comparison could not run: no reference, an old phone, a failed download. */
  | { outcome: 'UNAVAILABLE' };

const MODEL_URL = '/models/face';
const COMPARE_TIMEOUT_MS = 30000;

let loading: Promise<FaceApi> | null = null;

function load(): Promise<FaceApi> {
  loading ??= (async () => {
    const faceapi = await import('@vladmandic/face-api');
    // Picks WebGL where the phone has it, the CPU where it does not.
    await (faceapi.tf as unknown as { ready?: () => Promise<void> }).ready?.();
    await Promise.all([
      faceapi.nets.tinyFaceDetector.loadFromUri(MODEL_URL),
      faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL),
      faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL),
    ]);
    return faceapi;
  })().catch((err) => {
    // A failed download is retried next time rather than cached as a failure.
    loading = null;
    throw err;
  });
  return loading;
}

/** Starts the download early, while she is still reading the first screen. */
export function preloadFaceModels(): void {
  load().catch(() => undefined);
}

async function toCanvas(source: Blob): Promise<HTMLCanvasElement> {
  const bitmap = await createImageBitmap(source);
  // Faces in a selfie are large; 640 px is plenty and keeps a cheap phone quick.
  const scale = Math.min(1, 640 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close?.();
  return canvas;
}

/**
 * The small face finder is quick but sensitive to how big the face is in
 * the frame - a phone held close and one held at arm's length want
 * different scales. Tried at a few before deciding there is no face, so an
 * honest selfie is not sent back for a retake.
 */
const DETECTION_SIZES = [416, 320, 512, 608];

async function descriptorOf(faceapi: FaceApi, source: Blob): Promise<Float32Array | null> {
  const canvas = await toCanvas(source);
  for (const inputSize of DETECTION_SIZES) {
    const found = await faceapi
      .detectSingleFace(canvas, new faceapi.TinyFaceDetectorOptions({ inputSize, scoreThreshold: 0.4 }))
      .withFaceLandmarks()
      .withFaceDescriptor();
    if (found) return found.descriptor;
  }
  return null;
}

async function compare(referenceUrl: string | null, live: Blob): Promise<FaceComparison> {
  const faceapi = await load();
  const liveDescriptor = await descriptorOf(faceapi, live);
  if (!liveDescriptor) return { outcome: 'NO_FACE' };
  if (!referenceUrl) return { outcome: 'UNAVAILABLE' };
  const response = await fetch(referenceUrl);
  if (!response.ok) return { outcome: 'UNAVAILABLE' };
  const referenceDescriptor = await descriptorOf(faceapi, await response.blob());
  if (!referenceDescriptor) return { outcome: 'UNAVAILABLE' };
  return { outcome: 'COMPARED', distance: faceapi.euclideanDistance(liveDescriptor, referenceDescriptor) };
}

/**
 * Never throws and never hangs: anything that goes wrong is UNAVAILABLE, and
 * the check falls back to the photo alone - her working day does not depend
 * on a download.
 */
export async function compareFaces(referenceUrl: string | null, live: Blob): Promise<FaceComparison> {
  try {
    return await Promise.race([
      compare(referenceUrl, live),
      new Promise<FaceComparison>((resolve) => setTimeout(() => resolve({ outcome: 'UNAVAILABLE' }), COMPARE_TIMEOUT_MS)),
    ]);
  } catch {
    return { outcome: 'UNAVAILABLE' };
  }
}
