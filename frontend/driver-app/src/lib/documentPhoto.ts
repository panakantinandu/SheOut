/** Long enough on its longest edge to read the smallest print on a licence or policy. */
const MAX_EDGE = 2000;
const QUALITY = 0.88;
/** The server refuses smaller (DocumentRules.MIN_WIDTH x MIN_HEIGHT); such a photo is sent as it is, to be refused with its reason. */
const MIN_LONG = 480;
const MIN_SHORT = 320;

/**
 * A document photo as it leaves her phone: a camera original re-drawn as a
 * JPEG at most 2000px on its longest edge.
 * <p>
 * "Take photo" hands over whatever the camera made - often 4-12 MB, and on
 * some phones more than the 10 MB the server takes. Re-drawing it also drops
 * the EXIF block, which on a phone photo usually holds where it was taken:
 * her home. A PDF, a photo the browser cannot decode (an iPhone's HEIC
 * elsewhere than Safari) or one already too small to read goes as it is -
 * the server accepts HEIC, and says when a photo is too small.
 */
export async function prepareDocumentPhoto(file: File): Promise<File> {
  if (!file.type.startsWith('image/')) return file;
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    return file;
  }
  const long = Math.max(bitmap.width, bitmap.height);
  const short = Math.min(bitmap.width, bitmap.height);
  if (long < MIN_LONG || short < MIN_SHORT) {
    bitmap.close();
    return file;
  }
  const scale = Math.min(1, MAX_EDGE / long);
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  const context = canvas.getContext('2d');
  if (!context) {
    bitmap.close();
    return file;
  }
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', QUALITY));
  if (!blob) return file;
  const name = file.name.replace(/\.[^.]+$/, '') || 'document';
  return new File([blob], `${name}.jpg`, { type: 'image/jpeg' });
}
