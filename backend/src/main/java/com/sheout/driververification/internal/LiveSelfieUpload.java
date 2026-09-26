package com.sheout.driververification.internal;

import com.sheout.sharedkernel.storage.DocumentUpload;

/**
 * What the camera step sends: the facing-the-camera selfie, one image of the
 * frames taken during each prompt, and the challenge they answer.
 */
public record LiveSelfieUpload(DocumentUpload selfie, DocumentUpload livenessFrames, String challengeId) {
}
