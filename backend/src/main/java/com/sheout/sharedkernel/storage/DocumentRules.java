package com.sheout.sharedkernel.storage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * What counts as a document somebody can actually be verified from.
 * <p>
 * Nothing checked the uploads at all: an eleven-byte file named .png was
 * accepted, stored, and put in front of an operator as evidence of who
 * somebody is. Three things are checked here, in the order that gives the
 * most useful message back.
 * <p>
 * THE TYPE, BY ITS CONTENT. A photo of an ID or a scanned PDF are both
 * ordinary things to send, and the apps offer both; anything else cannot be
 * read by the console and is refused. The type the phone declares is not
 * consulted at all - it is whatever the phone felt like saying - only the
 * first bytes of the file, so a renamed one is refused rather than stored
 * as an image nobody can open.
 * <p>
 * A MINIMUM SIZE, MEASURED IN PIXELS WHERE IT CAN BE. What makes a document
 * readable is its resolution; bytes are only a proxy, and a poor one. A
 * photograph of a card at a legible resolution rarely comes under twenty
 * kilobytes, so that is the fallback - but a plain document, or one a phone
 * compressed well, can be small and perfectly readable, and refusing it
 * would be refusing a real person for the wrong reason. This caught the
 * app's own masking tool: an ID with its number covered re-encodes smaller,
 * and the byte rule alone turned her careful redaction into "that file is
 * too small to read". So when the dimensions can be read, they decide.
 * <p>
 * Either way the point is the same: something an operator cannot decide
 * from will be rejected anyway, a day later. Saying so at the moment she
 * taps upload is the kinder end of the same decision.
 * <p>
 * A MAXIMUM SIZE, so one upload cannot fill the request buffer. Spring
 * refuses larger multipart requests before this is reached; this exists so
 * the limit is stated in one place and the message is the app's own.
 */
public final class DocumentRules {

    /** Twenty kilobytes, for files whose dimensions cannot be read. */
    public static final int MIN_BYTES = 20 * 1024;

    /** A card photographed at less than this cannot be read by anybody. */
    public static final int MIN_WIDTH = 480;
    public static final int MIN_HEIGHT = 320;

    /** Ten megabytes, matching spring.servlet.multipart.max-file-size. */
    public static final int MAX_BYTES = 10 * 1024 * 1024;

    public enum Problem {
        /** Not a photo or a PDF, or the bytes do not match what it claims to be. */
        UNSUPPORTED_TYPE,
        /** Too small to be a readable document. */
        TOO_SMALL,
        /** Larger than the server accepts. */
        TOO_LARGE,
    }

    private DocumentRules() {
    }

    /** Null when the upload is usable, otherwise what is wrong with it. */
    public static Problem check(DocumentUpload upload) {
        byte[] bytes = upload.content();
        if (bytes == null || bytes.length == 0) {
            return Problem.TOO_SMALL;
        }
        if (bytes.length > MAX_BYTES) {
            return Problem.TOO_LARGE;
        }
        if (!looksLikeSupportedDocument(bytes)) {
            return Problem.UNSUPPORTED_TYPE;
        }
        Dimensions pixels = dimensionsOf(bytes);
        if (pixels != null) {
            return pixels.width() >= MIN_WIDTH && pixels.height() >= MIN_HEIGHT ? null : Problem.TOO_SMALL;
        }
        // A PDF, a HEIC, or an image Java has no reader for: fall back to
        // the size on disk, which is all that is left to judge by.
        if (bytes.length < MIN_BYTES) {
            return Problem.TOO_SMALL;
        }
        return null;
    }

    private record Dimensions(int width, int height) {
    }

    /** The image's own size, or null when it cannot be read here. */
    private static Dimensions dimensionsOf(byte[] bytes) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                // Header only - the pixels themselves are never decoded, so
                // this costs nothing and cannot be used to make the server
                // do work by uploading something enormous.
                reader.setInput(stream, true, true);
                return new Dimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * The first bytes, against the formats a person can be verified from.
     * <p>
     * Signature checks rather than the declared type: a phone will label
     * anything, and the console has to be able to open whatever is stored.
     * HEIC (what an iPhone takes by default) is recognised through its ISO
     * base-media brand, because refusing an iPhone photo would refuse a
     * large share of applicants.
     */
    private static boolean looksLikeSupportedDocument(byte[] b) {
        // A format nobody here recognises is refused even when the phone
        // insists it is an image: the console renders what it is given, and
        // a file it cannot render is a review that cannot happen.
        return isJpeg(b) || isPng(b) || isWebp(b) || isHeif(b) || isPdf(b);
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean isWebp(byte[] b) {
        return b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    /** HEIC/HEIF: an ISO base-media file whose brand starts with "hei" or "mif". */
    private static boolean isHeif(byte[] b) {
        if (b.length < 12 || b[4] != 'f' || b[5] != 't' || b[6] != 'y' || b[7] != 'p') {
            return false;
        }
        String brand = new String(b, 8, 4).toLowerCase(Locale.ROOT);
        return brand.startsWith("hei") || brand.startsWith("mif") || brand.startsWith("msf");
    }

    private static boolean isPdf(byte[] b) {
        return b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }
}
