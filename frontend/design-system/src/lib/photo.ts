/** The longest edge a profile photo is stored at - sharp on any phone screen, a fraction of a camera original. */
const MAX_EDGE = 1024;
const QUALITY = 0.85;

/**
 * Re-encodes a chosen photo as a JPEG at most 1024px on its longest edge,
 * before it is uploaded.
 * <p>
 * Three reasons, all of them about what a phone camera produces:
 * - size: a camera original is 3-8 MB; this is ~100-250 KB, and photos are
 *   stored in the database.
 * - format: iPhones save HEIC, which the server does not accept and most
 *   browsers cannot show; the browser that picked it can decode it, so it
 *   leaves as a JPEG.
 * - privacy: re-drawing the pixels drops the EXIF block, which on a phone
 *   photo usually includes the GPS position it was taken at - often her home.
 * <p>
 * Orientation is applied from the EXIF data before it is dropped, so a
 * portrait selfie does not arrive sideways.
 */
export async function shrinkPhoto(file: File): Promise<File> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    throw new Error('That file could not be read as a photo. Choose a JPEG or PNG image.');
  }
  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
  const width = Math.max(1, Math.round(bitmap.width * scale));
  const height = Math.max(1, Math.round(bitmap.height * scale));
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('This browser cannot prepare the photo. Try another browser.');
  context.drawImage(bitmap, 0, 0, width, height);
  bitmap.close();
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', QUALITY));
  if (!blob) throw new Error('This browser cannot prepare the photo. Try another browser.');
  return new File([blob], 'profile-photo.jpg', { type: 'image/jpeg' });
}
