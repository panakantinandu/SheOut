package com.sheout.driververification;

import java.time.Instant;
import java.util.List;

/**
 * The live selfie on file, for the reviewer to set beside the ID.
 * <p>
 * prompts is the order the server asked for them; livenessFramesUrl is one
 * image holding a frame for each, in that order. Signed, expiring links, the
 * same as the ID document's.
 */
public record LiveSelfie(String selfieUrl, String livenessFramesUrl, List<SelfiePrompt> prompts, Instant capturedAt) {
}
