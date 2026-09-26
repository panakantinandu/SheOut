package com.sheout.driververification;

/**
 * What she is asked to do in front of the camera before her selfie is taken.
 * <p>
 * Each is something a single still frame can show a reviewer - a turned
 * head, closed eyes, a smile - so the frame taken during it is evidence a
 * person can look at, and a photo held up to the camera cannot follow along.
 * The server picks them, at random, per attempt; see VerificationService.
 * This is a prompt for a human reviewer's judgement, not a liveness test.
 */
public enum SelfiePrompt {
    TURN_LEFT,
    TURN_RIGHT,
    LOOK_UP,
    SMILE,
    CLOSE_EYES
}
